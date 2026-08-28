package codeit.sb06.otboo.follow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.follow.dto.FollowCreateRequest;
import codeit.sb06.otboo.follow.dto.FollowDto;
import codeit.sb06.otboo.follow.entity.Follow;
import codeit.sb06.otboo.follow.repository.FollowRepository;
import codeit.sb06.otboo.profile.entity.Profile;
import codeit.sb06.otboo.profile.repository.ProfileRepository;
import codeit.sb06.otboo.notification.publisher.NotificationEventPublisher;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FollowCreateServiceTest {

  @InjectMocks
  private BasicFollowService basicFollowService;

  @Mock
  private FollowRepository followRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ProfileRepository profileRepository;

  @Mock
  private Profile followerProfile;

  @Mock
  private Profile followeeProfile;

  @Mock
  private NotificationEventPublisher notificationEventPublisher;

  UUID followerId, followeeId;
  User followee, follower;

  @BeforeEach
  void setUp() {
    followerId = UUID.randomUUID();
    followeeId = UUID.randomUUID();

    follower = mock(User.class);
    followee = mock(User.class);
  }

  @Test
  @DisplayName("팔로우 생성은 요청 followerId 대신 인증 사용자를 팔로워로 사용한다")
  void createFollow_success() {

    //given
    when(follower.getId()).thenReturn(followerId);
    when(follower.getName()).thenReturn("테스트 팔로워");
    when(follower.getProfileImageUrl()).thenReturn("테스트 팔로워 사진");

    when(followee.getId()).thenReturn(followeeId);
    when(followee.getName()).thenReturn("테스트 팔로위");
    when(followee.getProfileImageUrl()).thenReturn("테스트 팔로위 사진");

    FollowCreateRequest followCreateRequest = new FollowCreateRequest(followeeId, UUID.randomUUID());

    when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
    when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));

    when(profileRepository.findByUserId(follower))
        .thenReturn(Optional.of(followerProfile));
    when(profileRepository.findByUserId(followee))
        .thenReturn(Optional.of(followeeProfile));

    when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> invocation.getArgument(0));

    //when
    FollowDto result = basicFollowService.createFollow(followerId, followCreateRequest);

    //then
    // 결과가 널이 아닌지
    assertNotNull(result);
    // 저장된 값이 같은지
    assertEquals(followeeId, result.followee().userId());
    assertEquals(followerId, result.follower().userId());
    // 딱 한번 호출됬는지
    verify(followRepository,times(1)).save(any(Follow.class));

    // 증감이 한번씩 호출되었는지
    verify(followerProfile, times(1)).increaseFollowingCount();
    verify(followeeProfile, times(1)).increaseFollowerCount();
  }

  @Test
  void createFollow_fail_followeeNotFound() {

    // given
    FollowCreateRequest request = new FollowCreateRequest(followeeId, followerId);

    when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
    when(userRepository.findById(followeeId)).thenReturn(Optional.empty());

    // then
    assertThrows(UserNotFoundException.class, () -> basicFollowService.createFollow(followerId, request));
  }

  @Test
  void createFollow_fail_followerNotFound() {

    FollowCreateRequest request = new FollowCreateRequest(followeeId, followerId);

    when(userRepository.findById(followerId)).thenReturn(Optional.empty());

    assertThrows(UserNotFoundException.class, () -> basicFollowService.createFollow(followerId, request));

  }
}
