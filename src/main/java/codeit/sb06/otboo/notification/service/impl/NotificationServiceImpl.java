package codeit.sb06.otboo.notification.service.impl;

import codeit.sb06.otboo.exception.auth.ForbiddenException;
import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.dto.response.NotificationDtoCursorResponse;
import codeit.sb06.otboo.notification.entity.Notification;
import codeit.sb06.otboo.notification.enums.NotificationLevel;
import codeit.sb06.otboo.notification.mapper.NotificationMapper;
import codeit.sb06.otboo.notification.repository.NotificationRepository;
import codeit.sb06.otboo.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public NotificationDto create(UUID receiverId, String title, String content, NotificationLevel level) {

        return save(receiverId, title, content, level);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public NotificationDto createDirectMessageInCurrentTransaction(UUID receiverId, String senderName, String content) {

        String title = senderName + "님이 메시지를 보냈습니다.";

        return save(receiverId, title, content, NotificationLevel.INFO);
    }

    private NotificationDto save(UUID receiverId, String title, String content, NotificationLevel level) {

        Notification notification = Notification.builder()
                .receiverId(receiverId)
                .title(title)
                .content(content)
                .level(level)
                .build();

        Notification saved = notificationRepository.save(notification);

        log.debug("알림 생성: {}", saved);

        return notificationMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public NotificationDtoCursorResponse getNotifications(LocalDateTime cursor, UUID idAfter, int limit, UUID myUserId) {

        Slice<Notification> notifications;

        if(cursor == null && idAfter == null) {
            notifications = notificationRepository.findFirstPageByReceiverId(myUserId, PageRequest.of(0, limit));
        } else {
            notifications = notificationRepository.findByReceiverIdWithCursor(
                cursor,
                idAfter,
                myUserId,
                PageRequest.of(0, limit));
        }

        return notificationMapper.toDtoCursorResponse(notifications);
    }

    @Override
    public void deleteById(UUID currentUserId, UUID notificationId) {
        log.debug("알림 삭제: {}", notificationId);
        int deleted = notificationRepository.deleteByIdAndReceiverId(notificationId, currentUserId);

        if(deleted == 0) {
            throw new ForbiddenException();
        }
    }
}
