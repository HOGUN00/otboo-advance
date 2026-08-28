package codeit.sb06.otboo.notification.service;

import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.dto.response.NotificationDtoCursorResponse;
import codeit.sb06.otboo.notification.enums.NotificationLevel;

import java.time.LocalDateTime;
import java.util.UUID;

public interface NotificationService {

    NotificationDto create(UUID receiverId, String title, String content, NotificationLevel level);

    void deleteById(UUID currentUserId, UUID notificationId);

    NotificationDtoCursorResponse getNotifications(LocalDateTime cursor, UUID idAfter, int limit, UUID myUserId);
}
