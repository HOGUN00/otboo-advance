package codeit.sb06.otboo.feed.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.sb06.otboo.clothes.entity.Clothes;
import codeit.sb06.otboo.clothes.entity.ClothesType;
import codeit.sb06.otboo.clothes.repository.ClothesRepository;
import codeit.sb06.otboo.exception.clothes.ClothesNotFoundException;
import codeit.sb06.otboo.exception.feed.FeedNotFoundException;
import codeit.sb06.otboo.exception.auth.ForbiddenException;
import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.exception.weather.WeatherNotFoundException;
import codeit.sb06.otboo.feed.dto.FeedCreateRequest;
import codeit.sb06.otboo.feed.dto.FeedDto;
import codeit.sb06.otboo.feed.dto.FeedDtoCursorRequest;
import codeit.sb06.otboo.feed.dto.FeedDtoCursorResponse;
import codeit.sb06.otboo.feed.dto.FeedSortBy;
import codeit.sb06.otboo.feed.dto.FeedSortDirection;
import codeit.sb06.otboo.feed.dto.FeedUpdateRequest;
import codeit.sb06.otboo.feed.entity.Feed;
import codeit.sb06.otboo.feed.entity.FeedClothes;
import codeit.sb06.otboo.feed.entity.FeedLike;
import codeit.sb06.otboo.feed.repository.FeedLikeRepository;
import codeit.sb06.otboo.feed.repository.FeedRepository;
import codeit.sb06.otboo.follow.repository.FollowRepository;
import codeit.sb06.otboo.notification.publisher.NotificationEventPublisher;
import codeit.sb06.otboo.profile.service.S3StorageService;
import codeit.sb06.otboo.user.entity.Role;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.entity.Provider;
import codeit.sb06.otboo.user.repository.UserRepository;
import codeit.sb06.otboo.weather.dto.weather.PrecipitationType;
import codeit.sb06.otboo.weather.dto.weather.SkyStatus;
import codeit.sb06.otboo.weather.dto.weather.WindStrength;
import codeit.sb06.otboo.weather.entity.Weather;
import codeit.sb06.otboo.weather.repository.WeatherRepository;
import codeit.sb06.otboo.util.EasyRandomUtil;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.jeasy.random.EasyRandom;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();

    @InjectMocks
    private FeedService feedService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WeatherRepository weatherRepository;

    @Mock
    private ClothesRepository clothesRepository;

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedLikeRepository feedLikeRepository;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @Mock
    private S3StorageService s3StorageService;

    private UUID authorId;
    private UUID weatherId;
    private User author;
    private Weather weather;
    private Feed feed;

    @BeforeEach
    void setUp() {
        authorId = UUID.randomUUID();
        weatherId = UUID.randomUUID();

        author = new User(
            authorId,
            "user@example.com",
            "user",
            Provider.LOCAL,
            Role.USER,
            false,
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            "password",
            null,
            null
        );

        weather = Weather.builder()
            .id(weatherId)
            .skyStatus(SkyStatus.CLEAR)
            .precipitationType(PrecipitationType.NONE)
            .precipitationAmount(0.0)
            .precipitationProbability(0.0)
            .tempCurrent(15.0)
            .tempMin(10.0)
            .tempMax(20.0)
            .humidity(30.0)
            .windSpeed(2.0)
            .windStrength(WindStrength.WEAK)
            .build();

        feed = Feed.create(author, weather, List.of(), "content");
    }

    @Test
    @DisplayName("피드 생성은 요청 authorId 대신 인증 사용자를 작성자로 사용한다")
    void createFeed_success() {
        stubPresignedUrl();
        UUID spoofedAuthorId = UUID.randomUUID();
        UUID clothesId1 = UUID.randomUUID();
        UUID clothesId2 = UUID.randomUUID();
        List<UUID> clothesIds = List.of(clothesId1, clothesId2);

        Clothes clothes1 = new Clothes(authorId, "shirt", "img1", ClothesType.TOP);
        Clothes clothes2 = new Clothes(authorId, "pants", "img2", ClothesType.BOTTOM);

        FeedCreateRequest request = new FeedCreateRequest(
            spoofedAuthorId,
            weatherId,
            clothesIds,
            "content"
        );

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(weatherRepository.findById(weatherId)).thenReturn(Optional.of(weather));
        when(clothesRepository.findAllById(any())).thenReturn(List.of(clothes1, clothes2));
        when(feedRepository.save(any(Feed.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(followRepository.findByFolloweeId(any())).thenReturn(Collections.emptyList());

    FeedDto result = feedService.create(authorId, request);

    verify(feedRepository).save(any(Feed.class));
    assertNotNull(result);
    assertEquals("content", result.content());
    assertEquals(authorId, result.author().userId());
    assertEquals(2, result.ootds().size());
  }

    @Test
    @DisplayName("피드 생성 시 다른 사용자의 옷은 사용할 수 없다")
    void createFeed_rejectsClothesOwnedByAnotherUser() {
        UUID clothesId = UUID.randomUUID();
        Clothes otherUsersClothes = easyRandom.nextObject(Clothes.class);
        ReflectionTestUtils.setField(otherUsersClothes, "ownerId", UUID.randomUUID());
        FeedCreateRequest request = new FeedCreateRequest(
            UUID.randomUUID(), weatherId, List.of(clothesId), "content");

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(weatherRepository.findById(weatherId)).thenReturn(Optional.of(weather));
        when(clothesRepository.findAllById(any())).thenReturn(List.of(otherUsersClothes));

        assertThrows(ForbiddenException.class, () -> feedService.create(authorId, request));
        verify(feedRepository, never()).save(any());
    }

    @Test
    void createFeed_mapsFeedClothesToJoinEntities() {
        stubPresignedUrl();
        UUID clothesId1 = UUID.randomUUID();
        UUID clothesId2 = UUID.randomUUID();
        List<UUID> clothesIds = List.of(clothesId1, clothesId2);

        Clothes clothes1 = new Clothes(authorId, "shirt", "img1", ClothesType.TOP);
        Clothes clothes2 = new Clothes(authorId, "pants", "img2", ClothesType.BOTTOM);

        FeedCreateRequest request = new FeedCreateRequest(
            authorId,
            weatherId,
            clothesIds,
            "content"
        );

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(weatherRepository.findById(weatherId)).thenReturn(Optional.of(weather));
        when(clothesRepository.findAllById(any())).thenReturn(List.of(clothes1, clothes2));
        when(feedRepository.save(any(Feed.class))).thenAnswer(invocation -> invocation.getArgument(0));

        feedService.create(authorId, request);

        ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        Feed saved = captor.getValue();

        assertEquals(2, saved.getFeedClothes().size());
        Set<Clothes> mappedClothes = saved.getFeedClothes().stream()
            .map(FeedClothes::getClothes)
            .collect(Collectors.toSet());
        assertEquals(Set.of(clothes1, clothes2), mappedClothes);
    }

    @Test
    void createFeed_allowsDuplicateClothesIds() {
        stubPresignedUrl();
        UUID clothesId1 = UUID.randomUUID();
        List<UUID> clothesIds = List.of(clothesId1, clothesId1);

        Clothes clothes1 = new Clothes(authorId, "shirt", "img1", ClothesType.TOP);

        FeedCreateRequest request = new FeedCreateRequest(
            authorId,
            weatherId,
            clothesIds,
            "content"
        );

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(weatherRepository.findById(weatherId)).thenReturn(Optional.of(weather));
        when(clothesRepository.findAllById(any())).thenReturn(List.of(clothes1));
        when(feedRepository.save(any(Feed.class))).thenAnswer(invocation -> invocation.getArgument(0));

        feedService.create(authorId, request);

        ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        Feed saved = captor.getValue();

        assertEquals(1, saved.getFeedClothes().size());
        assertEquals(clothes1, saved.getFeedClothes().get(0).getClothes());
    }

  @Test
  void createFeed_throwsWhenClothesMissing() {
    UUID clothesId1 = UUID.randomUUID();
    UUID clothesId2 = UUID.randomUUID();
    List<UUID> clothesIds = List.of(clothesId1, clothesId2);

        Clothes clothes1 = new Clothes(authorId, "shirt", "img1", ClothesType.TOP);

        FeedCreateRequest request = new FeedCreateRequest(
            authorId,
            weatherId,
            clothesIds,
            "content"
        );

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(weatherRepository.findById(weatherId)).thenReturn(Optional.of(weather));
        when(clothesRepository.findAllById(any())).thenReturn(List.of(clothes1));

        assertThrows(ClothesNotFoundException.class, () -> feedService.create(authorId, request));
    }

    @Test
    void createFeed_throwsWhenUserMissing() {
        UUID clothesId1 = UUID.randomUUID();
        List<UUID> clothesIds = List.of(clothesId1);

        FeedCreateRequest request = new FeedCreateRequest(
            authorId,
            weatherId,
            clothesIds,
            "content"
        );

        when(userRepository.findById(authorId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> feedService.create(authorId, request));
    }

    @Test
    void createFeed_throwsWhenWeatherMissing() {
        UUID clothesId1 = UUID.randomUUID();
        List<UUID> clothesIds = List.of(clothesId1);

        Clothes clothes1 = new Clothes(authorId, "shirt", "img1", ClothesType.TOP);

        FeedCreateRequest request = new FeedCreateRequest(
            authorId,
            weatherId,
            clothesIds,
            "content"
        );

        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(weatherRepository.findById(weatherId)).thenReturn(Optional.empty());

        assertThrows(WeatherNotFoundException.class, () -> feedService.create(authorId, request));
    }

    @Test
    void deleteFeed_success() {
        UUID feedId = UUID.randomUUID();
        when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));

        feedService.delete(authorId, feedId);

        verify(feedRepository).delete(feed);
    }

    @Test
    void deleteFeed_throwsWhenFeedMissing() {
        UUID feedId = UUID.randomUUID();
        when(feedRepository.findById(feedId)).thenReturn(Optional.empty());

        assertThrows(FeedNotFoundException.class, () -> feedService.delete(authorId, feedId));
        verify(feedRepository, never()).delete(any());
    }

    @Test
    void updateFeed_ownerUpdatesContent() {
        UUID feedId = UUID.randomUUID();
        when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));

        FeedDto result = feedService.update(feedId, author.getId(), new FeedUpdateRequest("updated"));

        assertEquals("updated", result.content());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateFeed_adminUpdatesContent() {
        UUID feedId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User admin = new User(
            adminId,
            "admin@example.com",
            "admin",
            Provider.LOCAL,
            Role.ADMIN,
            false,
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            "password",
            null,
            null
        );

        when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        FeedDto result = feedService.update(feedId, adminId, new FeedUpdateRequest("updated-by-admin"));

        assertEquals("updated-by-admin", result.content());
        verify(userRepository, times(1)).findById(adminId);
    }

    @Test
    void updateFeed_throwsWhenFeedMissing() {
        UUID feedId = UUID.randomUUID();
        when(feedRepository.findById(feedId)).thenReturn(Optional.empty());

        assertThrows(FeedNotFoundException.class, () -> feedService.update(feedId, author.getId(), new FeedUpdateRequest("updated")));
    }

    @Test
    void updateFeed_throwsWhenNotOwnerAndUserMissing() {
        UUID feedId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> feedService.update(feedId, otherUserId, new FeedUpdateRequest("updated")));
    }

    @Test
    void updateFeed_throwsWhenNotOwnerAndNotAdmin() {
        UUID feedId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        User otherUser = new User(
            otherUserId,
            "other@example.com",
            "other",
            Provider.LOCAL,
            Role.USER,
            false,
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            "password",
            null,
            null
        );

        when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));

        assertThrows(ForbiddenException.class, () -> feedService.update(feedId, otherUserId, new FeedUpdateRequest("updated")));
    }

    @Test
    void like_createsLikeAndIncrementsCount() {
        UUID feedId = UUID.randomUUID();

        when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
        when(feedLikeRepository.existsByFeedIdAndUserId(feedId, author.getId())).thenReturn(false);

        feedService.like(feedId, author.getId());

        verify(feedLikeRepository, times(1)).save(any(FeedLike.class));
        assertEquals(1L, feed.getLikeCount());
    }

    @Test
    void like_isIdempotentWhenAlreadyLiked() {
        UUID feedId = UUID.randomUUID();

        when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
        when(feedLikeRepository.existsByFeedIdAndUserId(feedId, author.getId())).thenReturn(true);

        feedService.like(feedId, author.getId());

        verify(feedLikeRepository, never()).save(any(FeedLike.class));
        assertEquals(0L, feed.getLikeCount());
    }

    @Test
    void unlike_removesLikeAndDecrementsCount() {
        UUID feedId = UUID.randomUUID();

        when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
        when(feedLikeRepository.findByFeedIdAndUserId(feedId, author.getId()))
            .thenReturn(Optional.of(FeedLike.create(author, feed)));

        feedService.unlike(feedId, author.getId());

        verify(feedLikeRepository, times(1)).delete(any(FeedLike.class));
        assertEquals(0L, feed.getLikeCount());
    }

    @Test
    void unlike_noopWhenNotLiked() {
        UUID feedId = UUID.randomUUID();

        when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
        when(feedLikeRepository.findByFeedIdAndUserId(feedId, author.getId()))
            .thenReturn(Optional.empty());

        feedService.unlike(feedId, author.getId());

        verify(feedLikeRepository, never()).delete(any(FeedLike.class));
        assertEquals(0L, feed.getLikeCount());
    }

    @Test
    void getFeeds_includesLikedByMeAndCursorWhenHasNext() {
        UUID currentUserId = UUID.randomUUID();
        FeedDtoCursorRequest request = new FeedDtoCursorRequest(
            null,
            null,
            2,
            "createdAt",
            FeedSortDirection.DESCENDING,
            null,
            null,
            null,
            null
        );

        Feed feed1 = feedWithIdAndCreatedAt(UUID.randomUUID(), LocalDateTime.now().minusMinutes(3));
        Feed feed2 = feedWithIdAndCreatedAt(UUID.randomUUID(), LocalDateTime.now().minusMinutes(2));
        Feed feed3 = feedWithIdAndCreatedAt(UUID.randomUUID(), LocalDateTime.now().minusMinutes(1));

        when(feedRepository.findFeedListByCursor(request, 3)).thenReturn(List.of(feed1, feed2, feed3));
        when(feedLikeRepository.findFeedIdsByUserIdAndFeedIdIn(currentUserId, List.of(feed1.getId(), feed2.getId())))
            .thenReturn(List.of(feed1.getId()));
        when(feedRepository.countFeedList(request)).thenReturn(5L);

        FeedDtoCursorResponse response = feedService.getFeeds(currentUserId, request);

        assertEquals(2, response.data().size());
        assertEquals(true, response.data().get(0).likedByMe());
        assertEquals(false, response.data().get(1).likedByMe());
        assertEquals(true, response.hasNext());
        assertEquals(feed2.getId(), response.nextIdAfter());
        assertEquals(feed2.getCreatedAt().toString(), response.nextCursor());
        assertEquals(5L, response.totalCount());
    }

    @Test
    void getFeeds_setsHasNextFalseWhenResultsFitInLimit() {
        UUID currentUserId = UUID.randomUUID();
        FeedDtoCursorRequest request = new FeedDtoCursorRequest(
            null,
            null,
            3,
            "createdAt",
            FeedSortDirection.DESCENDING,
            null,
            null,
            null,
            null
        );

        Feed feed1 = feedWithIdAndCreatedAt(UUID.randomUUID(), LocalDateTime.now().minusMinutes(2));
        Feed feed2 = feedWithIdAndCreatedAt(UUID.randomUUID(), LocalDateTime.now().minusMinutes(1));

        when(feedRepository.findFeedListByCursor(request, 4)).thenReturn(List.of(feed1, feed2));
        when(feedLikeRepository.findFeedIdsByUserIdAndFeedIdIn(currentUserId, List.of(feed1.getId(), feed2.getId())))
            .thenReturn(List.of());
        when(feedRepository.countFeedList(request)).thenReturn(2L);

        FeedDtoCursorResponse response = feedService.getFeeds(currentUserId, request);

        assertEquals(2, response.data().size());
        assertEquals(false, response.hasNext());
        assertEquals(null, response.nextIdAfter());
        assertEquals(null, response.nextCursor());
        assertEquals(2L, response.totalCount());
    }

    private Feed feedWithIdAndCreatedAt(UUID feedId, LocalDateTime createdAt) {
        Feed created = Feed.create(author, weather, List.of(), "content");
        ReflectionTestUtils.setField(created, "id", feedId);
        ReflectionTestUtils.setField(created, "createdAt", createdAt);
        ReflectionTestUtils.setField(created, "updatedAt", createdAt);
        return created;
    }

    private void stubPresignedUrl() {
        when(s3StorageService.getPresignedUrl(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
