package codeit.sb06.otboo.notification.service;

import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.exception.auth.ForbiddenException;
import codeit.sb06.otboo.notification.dto.response.NotificationDtoCursorResponse;
import codeit.sb06.otboo.notification.entity.Notification;
import codeit.sb06.otboo.notification.enums.NotificationLevel;
import codeit.sb06.otboo.notification.mapper.NotificationMapper;
import codeit.sb06.otboo.notification.repository.NotificationRepository;
import codeit.sb06.otboo.notification.service.impl.NotificationServiceImpl;
import codeit.sb06.otboo.util.EasyRandomUtil;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();

    @Mock
    private NotificationRepository notificationRepository;

    @Spy
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    @DisplayName("알림 생성 성공 테스트")
    void createNotificationTest() {
        // given
        UUID receiverId = UUID.randomUUID();
        String title = "Test Title";
        String content = "Test Content";
        NotificationLevel level = NotificationLevel.INFO;

        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        // when
        NotificationDto notificationDto = notificationService.create(receiverId, title, content, level);

        // then
        assertThat(notificationDto).isNotNull();
    }

    @Test
    @DisplayName("DM 알림 생성 시 제목과 등급을 설정한다")
    void createDirectMessageNotificationTest() {
        // given
        UUID receiverId = UUID.randomUUID();
        String senderName = "sender";
        String content = "message";
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        NotificationDto notificationDto = notificationService.createDirectMessageInCurrentTransaction(
                receiverId,
                senderName,
                content);

        // then
        assertAll(
                () -> assertThat(notificationDto.receiverId()).isEqualTo(receiverId),
                () -> assertThat(notificationDto.title()).isEqualTo("sender님이 메시지를 보냈습니다."),
                () -> assertThat(notificationDto.content()).isEqualTo(content),
                () -> assertThat(notificationDto.level()).isEqualTo(NotificationLevel.INFO)
        );
    }

    @Test
    @DisplayName("알림 커서페이지네이션 조회 성공 테스트")
    void getNotificationsTest() {
        // given
        int limit = 10;
        UUID myUserId = UUID.randomUUID();
        List<Notification> notifications = easyRandom.objects(Notification.class, limit + limit).toList();
        List<Notification> top10 = notifications.subList(0, 10);
        Slice<Notification> notificationSlice = new SliceImpl<>(top10, PageRequest.of(0, 10), true);

        given(notificationRepository.findFirstPageByReceiverId(
                myUserId,
                PageRequest.of(0, limit)))
                .willReturn(notificationSlice);

        // when
        NotificationDtoCursorResponse response = notificationService.getNotifications(
                null,
                null,
                limit,
                myUserId
        );

        // then
        assertAll(
                () -> assertThat(response).isNotNull(),
                () -> assertThat(response.data()).hasSize(limit)
        );
    }

    @Test
    @DisplayName("알림 삭제 성공 테스트")
    void deleteNotificationTest() {
        // given
        UUID notificationId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        given(notificationRepository.deleteByIdAndReceiverId(notificationId, currentUserId))
            .willReturn(1);

        // when
        notificationService.deleteById(currentUserId, notificationId);

        // then
        verify(notificationRepository).deleteByIdAndReceiverId(notificationId, currentUserId);
    }

    @Test
    @DisplayName("수신자가 아닌 사용자는 알림을 삭제할 수 없다")
    void deleteNotificationRejectsNonReceiver() {
        UUID notificationId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        given(notificationRepository.deleteByIdAndReceiverId(notificationId, currentUserId))
            .willReturn(0);

        assertThrows(
            ForbiddenException.class,
            () -> notificationService.deleteById(currentUserId, notificationId)
        );
    }
}
