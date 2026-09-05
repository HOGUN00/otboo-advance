package codeit.sb06.otboo.notification.publisher;

import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.user.entity.Role;

import java.util.UUID;

public interface NotificationEventPublisher {
    void publishNotificationCreatedEvent(NotificationDto notificationDto);
    void publishRoleUpdatedEvent(UUID targetId, Role role);
    void publishClothesAttributeAddedEvent(UUID targetId, String attributeName);
    void publishFeedLikedEvent(UUID targetId, String feedTitle, String likerName);
    void publishFeedCommentedEvent(UUID targetId, String commenterName, String feedTitle, String content);
    void publishFolloweeFeedPostedEvent(UUID targetId, String followeeName, String feedTitle);
    void publishFollowedEvent(UUID targetId, String followerName);
}
