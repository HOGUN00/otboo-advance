package codeit.sb06.otboo.notification.listener;

import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.enums.NotificationLevel;
import codeit.sb06.otboo.notification.event.*;
import codeit.sb06.otboo.notification.publisher.RedisNotificationPublisher;
import codeit.sb06.otboo.notification.service.NotificationCacheService;
import codeit.sb06.otboo.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final NotificationCacheService notificationCacheService;
    private final RedisNotificationPublisher redisNotificationPublisher;

    @Order(1)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationCreatedEvent(NotificationCreatedEvent event) {
        cacheAndPublish(event.notificationDto());
    }

    @TransactionalEventListener
    public void handleRoleUpdatedEvent(RoleUpdatedEvent event) {

        String title = "나의 권한이 " + event.role().name() + "(으)로 변경되었습니다.";

        createAndSend(event.targetId(), title, "");
    }

    @TransactionalEventListener
    public void handleClothesAttributeAddedEvent(ClothesAttributeAddedEvent event) {

        String title = event.attributeName() + " 의상 속성이 추가되었습니다.";

        createAndSend(event.targetId(), title, "");
    }

    @TransactionalEventListener
    public void handleFeedLikedEvent(FeedLikedEvent event) {

        String title = "\"" +event.feedTitle() + "...\" 피드에 " + event.likerName() + "님이 좋아요를 눌렀습니다.";

        createAndSend(event.targetId(), title, "");
    }

    @TransactionalEventListener
    public void handleFeedCommentedEvent(FeedCommentedEvent event) {

        String title = event.commenterName() + "님이 \"" + event.feedTitle() + "...\" 피드에 댓글을 달았습니다.";

        createAndSend(event.targetId(), title, event.content());
    }

    @TransactionalEventListener
    public void handleFolloweeFeedPostedEvent(FolloweeFeedPostedEvent event) {

        String title = event.followeeName() + "님이 \"" + event.feedTitle() + "...\" 피드를 게시했습니다.";

        createAndSend(event.targetId(), title, "");
    }

    @TransactionalEventListener
    public void handleFollowedEvent(FollowedEvent event) {

        String title = event.followerName() + "님이 회원님을 팔로우했습니다.";

        createAndSend(event.targetId(), title, "");
    }

    private void createAndSend(UUID targetId, String title, String content) {

        NotificationDto notificationDto = notificationService.create(
                targetId,
                title,
                content,
                NotificationLevel.INFO);

        cacheAndPublish(notificationDto);
    }

    private void cacheAndPublish(NotificationDto notificationDto) {
        notificationCacheService.save(notificationDto);
        redisNotificationPublisher.publish(notificationDto);
    }
}
