package codeit.sb06.otboo.notification.event;

import codeit.sb06.otboo.notification.dto.NotificationDto;

public record NotificationCreatedEvent(
        NotificationDto notificationDto
) {
}
