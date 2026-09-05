package codeit.sb06.otboo.notification.listener;

import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.enums.NotificationLevel;
import codeit.sb06.otboo.notification.event.*;
import codeit.sb06.otboo.notification.publisher.RedisNotificationPublisher;
import codeit.sb06.otboo.notification.service.NotificationCacheService;
import codeit.sb06.otboo.notification.service.NotificationService;
import codeit.sb06.otboo.util.EasyRandomUtil;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();
    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationCacheService notificationCacheService;

    @Mock
    private RedisNotificationPublisher redisNotificationPublisher;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    @DisplayName("저장된 알림 이벤트 수신 시 캐시 저장과 Redis 발행을 수행한다")
    void notificationCreatedEventTest() {
        // given
        NotificationDto notification = easyRandom.nextObject(NotificationDto.class);
        NotificationCreatedEvent event = new NotificationCreatedEvent(notification);

        // when
        listener.handleNotificationCreatedEvent(event);

        // then
        verify(notificationCacheService).save(notification);
        verify(redisNotificationPublisher).publish(notification);
    }

    @Test
    @DisplayName("권한 변경 이벤트 수신 시 알림이 생성된다.")
    void RoleUpdatedEventTest() {
        // given
        var event = easyRandom.nextObject(RoleUpdatedEvent.class);

        // when
        listener.handleRoleUpdatedEvent(event);

        // then
        verify(notificationService, times(1)).create(
                event.targetId(),
                "나의 권한이 " + event.role().name() + "(으)로 변경되었습니다.",
                "",
                NotificationLevel.INFO
        );
    }

    @Test
    @DisplayName("의상 속성 추가 이벤트 수신 시 알림이 생성된다.")
    void ClothesAttributeAddedEventTest() {
        // given
        var event = easyRandom.nextObject(ClothesAttributeAddedEvent.class);

        // when
        listener.handleClothesAttributeAddedEvent(event);

        // then
        verify(notificationService, times(1)).create(
                event.targetId(),
                event.attributeName() + " 의상 속성이 추가되었습니다.",
                "",
                NotificationLevel.INFO
        );
    }

    @Test
    @DisplayName("피드 좋아요 이벤트 수신 시 알림이 생성된다.")
    void FeedLikedEventTest() {
        // given
        var event = easyRandom.nextObject(FeedLikedEvent.class);

        // when
        listener.handleFeedLikedEvent(event);

        // then
        verify(notificationService, times(1)).create(
                event.targetId(),
                "\"" +event.feedTitle() + "...\" 피드에 " + event.likerName() + "님이 좋아요를 눌렀습니다.",
                "",
                NotificationLevel.INFO
        );
    }

    @Test
    @DisplayName("피드 댓글 이벤트 수신 시 알림이 생성된다.")
    void FeedCommentedEventTest() {
        // given
        var event = easyRandom.nextObject(FeedCommentedEvent.class);

        // when
        listener.handleFeedCommentedEvent(event);

        // then
        verify(notificationService, times(1)).create(
                event.targetId(),
                event.commenterName() + "님이 \"" + event.feedTitle() + "...\" 피드에 댓글을 달았습니다.",
                event.content(),
                NotificationLevel.INFO
        );
    }

    @Test
    @DisplayName("팔로워 피드 게시 이벤트 수신 시 알림이 생성된다.")
    void FolloweeFeedPostedEventTest() {
        // given
        var event = easyRandom.nextObject(FolloweeFeedPostedEvent.class);

        // when
        listener.handleFolloweeFeedPostedEvent(event);

        // then
        verify(notificationService, times(1)).create(
                event.targetId(),
                event.followeeName() + "님이 \"" + event.feedTitle() + "...\" 피드를 게시했습니다.",
                "",
                NotificationLevel.INFO
        );
    }

    @Test
    @DisplayName("팔로우 이벤트 수신 시 알림이 생성된다.")
    void FollowedEventTest() {
        // given
        var event = easyRandom.nextObject(FollowedEvent.class);

        // when
        listener.handleFollowedEvent(event);

        // then
        verify(notificationService, times(1)).create(
                event.targetId(),
                event.followerName() + "님이 회원님을 팔로우했습니다.",
                "",
                NotificationLevel.INFO
        );
    }
}
