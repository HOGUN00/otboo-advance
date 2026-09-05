package codeit.sb06.otboo.notification.publisher;

import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.event.*;
import codeit.sb06.otboo.notification.publisher.impl.NotificationEventPublisherImpl;
import codeit.sb06.otboo.util.EasyRandomUtil;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();

    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private NotificationEventPublisherImpl notificationEventPublisher;

    @Test
    @DisplayName("저장된 알림 이벤트가 발행된다")
    void notificationCreatedEventPublishTest() {
        // given
        NotificationDto notification = easyRandom.nextObject(NotificationDto.class);

        // when
        notificationEventPublisher.publishNotificationCreatedEvent(notification);

        // then
        verify(publisher).publishEvent(new NotificationCreatedEvent(notification));
    }

    @Test
    @DisplayName("역할 업데이트 이벤트가 발행된다.")
    void roleUpdatedEventPublishTest() {
        // when
        RoleUpdatedEvent event = easyRandom.nextObject(RoleUpdatedEvent.class);

        // then
        notificationEventPublisher.publishRoleUpdatedEvent(
                event.targetId(), event.role()
        );

        // then
        verify(publisher, times(1)).publishEvent(event);
    }

    @Test
    @DisplayName("의상 속성 추가 이벤트가 발행된다.")
    void clothesAttributeAddedEventPublishTest() {
        // when
        ClothesAttributeAddedEvent event = easyRandom.nextObject(ClothesAttributeAddedEvent.class);

        // then
        notificationEventPublisher.publishClothesAttributeAddedEvent(
                event.targetId(), event.attributeName()
        );

        // then
        verify(publisher, times(1)).publishEvent(event);
    }

    @Test
    @DisplayName("피드 좋아요 이벤트가 발행된다.")
    void feedLikedEventPublishTest() {
        // when
        FeedLikedEvent event = easyRandom.nextObject(FeedLikedEvent.class);

        // then
        notificationEventPublisher.publishFeedLikedEvent(
                event.targetId(), event.feedTitle(), event.likerName()
        );

        // then
        verify(publisher, times(1)).publishEvent(event);
    }

    @Test
    @DisplayName("피드 댓글 이벤트가 발행된다.")
    void feedCommentedEventPublishTest() {
        // given
        FeedCommentedEvent event = easyRandom.nextObject(FeedCommentedEvent.class);

        // when
        notificationEventPublisher.publishFeedCommentedEvent(
                event.targetId(), event.commenterName(), event.feedTitle(), event.content()
        );

        // then
        verify(publisher, times(1)).publishEvent(event);
    }

    @Test
    @DisplayName("팔로우한 피드 게시 이벤트가 발행된다.")
    void followeeFeedPostedEventPublishTest() {
        // given
        FolloweeFeedPostedEvent event = easyRandom.nextObject(FolloweeFeedPostedEvent.class);

        // when
        notificationEventPublisher.publishFolloweeFeedPostedEvent(
                event.targetId(), event.followeeName(), event.feedTitle()
        );

        // then
        verify(publisher, times(1)).publishEvent(event);
    }

    @Test
    @DisplayName("팔로우된 이벤트가 발행된다.")
    void followedEventPublishTest() {
        // given
        FollowedEvent event = easyRandom.nextObject(FollowedEvent.class);

        // when
        notificationEventPublisher.publishFollowedEvent(
                event.targetId(), event.followerName()
        );

        // then
        verify(publisher, times(1)).publishEvent(event);
    }
}
