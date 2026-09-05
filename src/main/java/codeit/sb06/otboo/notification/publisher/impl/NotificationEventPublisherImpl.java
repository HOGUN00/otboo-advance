package codeit.sb06.otboo.notification.publisher.impl;

import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.event.*;
import codeit.sb06.otboo.notification.publisher.NotificationEventPublisher;
import codeit.sb06.otboo.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationEventPublisherImpl implements NotificationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publishNotificationCreatedEvent(NotificationDto notificationDto) {
        eventPublisher.publishEvent(new NotificationCreatedEvent(notificationDto));
    }

    @Override
    public void publishRoleUpdatedEvent(UUID targetId, Role role) {

        RoleUpdatedEvent event = RoleUpdatedEvent.builder()
                .targetId(targetId)
                .role(role)
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishClothesAttributeAddedEvent(UUID targetId, String attributeName) {

        ClothesAttributeAddedEvent event = ClothesAttributeAddedEvent.builder()
                .targetId(targetId)
                .attributeName(attributeName)
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishFeedLikedEvent(UUID targetId, String feedTitle, String likerName) {

        FeedLikedEvent event = FeedLikedEvent.builder()
                .targetId(targetId)
                .feedTitle(feedTitle)
                .likerName(likerName)
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishFeedCommentedEvent(UUID targetId, String commenterName,String feedTitle, String content) {

        FeedCommentedEvent event = FeedCommentedEvent.builder()
                .targetId(targetId)
                .commenterName(commenterName)
                .feedTitle(feedTitle)
                .content(content)
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishFolloweeFeedPostedEvent(UUID targetId, String followeeName, String feedTitle) {

        FolloweeFeedPostedEvent event = FolloweeFeedPostedEvent.builder()
                .targetId(targetId)
                .followeeName(followeeName)
                .feedTitle(feedTitle)
                .build();

        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishFollowedEvent(UUID targetId, String followerName) {

        FollowedEvent event = FollowedEvent.builder()
                .targetId(targetId)
                .followerName(followerName)
                .build();

        eventPublisher.publishEvent(event);
    }
}
