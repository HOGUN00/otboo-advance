package codeit.sb06.otboo.notification.controller;

import codeit.sb06.otboo.notification.dto.response.NotificationDtoCursorResponse;
import codeit.sb06.otboo.notification.service.NotificationService;
import codeit.sb06.otboo.security.resolver.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationDtoCursorResponse> getNotifications(
            @RequestParam(required = false) LocalDateTime cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam int limit,
            @CurrentUserId UUID myUserId
            ) {
        NotificationDtoCursorResponse response = notificationService.getNotifications(cursor, idAfter, limit, myUserId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{notificationId}")
    public void deleteNotification(
            @CurrentUserId UUID currentUserId,
            @PathVariable UUID notificationId
    ) {
        notificationService.deleteById(currentUserId, notificationId);
    }
}
