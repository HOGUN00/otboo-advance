package codeit.sb06.otboo.message.dto;

import codeit.sb06.otboo.notification.dto.NotificationDto;

public record DirectMessageCreation(
        DirectMessageDto directMessageDto,
        NotificationDto notificationDto,
        String destination
) {
}
