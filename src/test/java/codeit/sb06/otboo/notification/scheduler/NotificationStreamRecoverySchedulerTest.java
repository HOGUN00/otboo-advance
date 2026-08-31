package codeit.sb06.otboo.notification.scheduler;

import codeit.sb06.otboo.config.RedisStreamProperties;
import codeit.sb06.otboo.notification.service.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationStreamRecoverySchedulerTest {

    private final String notificationStreamKey = "test-noti-stream";
    private final String groupName = "group-noti-server1";
    private final String serverId = "server1";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, String, String> streamOps;

    @Mock
    private SseService sseService;
    private NotificationStreamRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        RedisStreamProperties streamProperties =
                new RedisStreamProperties(notificationStreamKey, "test-dm-stream");
        scheduler = new NotificationStreamRecoveryScheduler(
                redisTemplate, null, serverId, streamProperties, sseService);
        ReflectionTestUtils.setField(scheduler, "groupName", groupName);

        doReturn(streamOps).when(redisTemplate).opsForStream();
    }

    @Test
    @DisplayName("재전송 5회 초과 알림 메시지는 Claim 없이 바로 ACK 처리되어야 한다 (Poison Message 처리)")
    void processPendingMessage_DropPoisonMessage() {
        // given: 1분(60초) 이상 지났고, 6번 시도한 PendingMessage 모킹
        PendingMessage msg = mock(PendingMessage.class);
        RecordId recordId = RecordId.of("1612345678-0");
        given(msg.getId()).willReturn(recordId);
        given(msg.getElapsedTimeSinceLastDelivery()).willReturn(Duration.ofSeconds(65));
        given(msg.getTotalDeliveryCount()).willReturn(6L);

        PendingMessages pendingMessages = new PendingMessages(groupName, List.of(msg));
        given(streamOps.pending(notificationStreamKey, groupName, Range.unbounded(), 100L))
                .willReturn(pendingMessages);

        // when
        scheduler.recoverNotificationMessages();

        // then: acknowledge는 호출되어야 하고, claim은 호출되지 않아야 함
        verify(streamOps, times(1)).acknowledge(notificationStreamKey, groupName, recordId);
        verify(streamOps, never()).claim(any(), any(), any(), any());
        verify(sseService, never()).send(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("조건을 만족하는 알림 메시지는 Claim 후 SSE로 전송하고 ACK 처리되어야 한다")
    void processPendingMessage_SuccessRecovery() {
        // given: 1분(60초) 지났고, 시도 횟수는 1번인 정상 대기 메시지
        PendingMessage msg = mock(PendingMessage.class);
        RecordId recordId = RecordId.of("1612345678-0");
        given(msg.getId()).willReturn(recordId);
        given(msg.getElapsedTimeSinceLastDelivery()).willReturn(Duration.ofSeconds(62));
        given(msg.getTotalDeliveryCount()).willReturn(1L);

        PendingMessages pendingMessages = new PendingMessages(groupName, List.of(msg));
        given(streamOps.pending(notificationStreamKey, groupName, Range.unbounded(), 100L))
                .willReturn(pendingMessages);

        // Claim 했을 때 반환될 실제 메시지 데이터 모킹
        String receiverUuid = UUID.randomUUID().toString();
        MapRecord<String, String, String> claimedRecord = MapRecord.create(notificationStreamKey,
                        Map.of("payload", "hello noti", "receiverId", receiverUuid))
                .withId(recordId);

        given(streamOps.claim(eq(notificationStreamKey), eq(groupName), eq("notification-recover-worker-" + serverId), any()))
                .willReturn(List.of(claimedRecord));

        // when
        scheduler.recoverNotificationMessages();

        // then: SSE 전송이 이루어지고, 정상적으로 ACK 처리되어야 함
        verify(sseService, times(1)).send(UUID.fromString(receiverUuid), "notifications", "hello noti");
        verify(streamOps, times(1)).acknowledge(notificationStreamKey, groupName, recordId);
    }

    @Test
    @DisplayName("대기 시간이 1분 미만인 알림 메시지는 무시되어야 한다")
    void processPendingMessage_IgnoreIfIdleTimeNotMet() {
        // given: 30초밖에 안 지난 메시지
        PendingMessage msg = mock(PendingMessage.class);
        given(msg.getElapsedTimeSinceLastDelivery()).willReturn(Duration.ofSeconds(30));

        PendingMessages pendingMessages = new PendingMessages(groupName, List.of(msg));
        given(streamOps.pending(notificationStreamKey, groupName, Range.unbounded(), 100L))
                .willReturn(pendingMessages);

        // when
        scheduler.recoverNotificationMessages();

        // then: 아무런 동작(Claim, Ack, Send)도 일어나지 않아야 함
        verify(streamOps, never()).acknowledge(any(), any(), any(RecordId.class));
        verify(streamOps, never()).claim(any(), any(), any(), any());
    }
}
