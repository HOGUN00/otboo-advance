package codeit.sb06.otboo.message.scheduler;

import codeit.sb06.otboo.config.RedisStreamProperties;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DmStreamRecoverySchedulerTest {

    private final String dmStreamKey = "test-dm-stream";
    private final String groupName = "group-dm-server1";
    private final String serverId = "server1";
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private StreamOperations<String, String, String> streamOps;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    private DmStreamRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        RedisStreamProperties streamProperties =
                new RedisStreamProperties("test-noti-stream", dmStreamKey);
        scheduler = new DmStreamRecoveryScheduler(
                redisTemplate, null, serverId, streamProperties, messagingTemplate);
        ReflectionTestUtils.setField(scheduler, "groupName", groupName);

        doReturn(streamOps).when(redisTemplate).opsForStream();
    }

    @Test
    @DisplayName("재전송 5회 초과 메시지는 Claim 없이 바로 ACK 처리되어야 한다 (Poison Message 처리)")
    void processPendingMessage_DropPoisonMessage() {
        // given: 10초 이상 지났고, 6번 시도한 PendingMessage 모킹
        PendingMessage msg = mock(PendingMessage.class);
        RecordId recordId = RecordId.of("1612345678-0");
        when(msg.getId()).thenReturn(recordId);
        when(msg.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofSeconds(15));
        when(msg.getTotalDeliveryCount()).thenReturn(6L);

        PendingMessages pendingMessages = new PendingMessages(groupName, List.of(msg));
        when(streamOps.pending(dmStreamKey, groupName, Range.unbounded(), 100L))
                .thenReturn(pendingMessages);

        // when
        scheduler.recoverDmMessages();

        // then: acknowledge는 호출되어야 하고, claim은 호출되지 않아야 함
        verify(streamOps, times(1)).acknowledge(dmStreamKey, groupName, recordId);
        verify(streamOps, never()).claim(any(), any(), any(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("조건을 만족하는 메시지는 Claim 후 WebSocket으로 전송하고 ACK 처리되어야 한다")
    void processPendingMessage_SuccessRecovery() {
        // given: 10초 지났고, 시도 횟수는 1번인 정상 대기 메시지
        PendingMessage msg = mock(PendingMessage.class);
        RecordId recordId = RecordId.of("1612345678-0");
        when(msg.getId()).thenReturn(recordId);
        when(msg.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofSeconds(12));
        when(msg.getTotalDeliveryCount()).thenReturn(1L);

        PendingMessages pendingMessages = new PendingMessages(groupName, List.of(msg));
        when(streamOps.pending(dmStreamKey, groupName, Range.unbounded(), 100L))
                .thenReturn(pendingMessages);

        // Claim 했을 때 반환될 실제 메시지 데이터 모킹
        MapRecord<String, String, String> claimedRecord = MapRecord.create(dmStreamKey,
                        Map.of("payload", "hello", "destination", "/sub/dm/1", "receiverId", "user1"))
                .withId(recordId);

        when(streamOps.claim(eq(dmStreamKey), eq(groupName), eq("dm-recover-worker-" + serverId), any()))
                .thenReturn(List.of(claimedRecord));

        // when
        scheduler.recoverDmMessages();

        // then: WebSocket 전송이 이루어지고, 정상적으로 ACK 처리되어야 함
        verify(messagingTemplate, times(1)).convertAndSend("/sub/dm/1", "hello");
        verify(streamOps, times(1)).acknowledge(dmStreamKey, groupName, recordId);
    }

    @Test
    @DisplayName("대기 시간이 10초 미만인 메시지는 무시되어야 한다")
    void processPendingMessage_IgnoreIfIdleTimeNotMet() {
        // given: 3초밖에 안 지난 메시지
        PendingMessage msg = mock(PendingMessage.class);
        when(msg.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofSeconds(3));

        PendingMessages pendingMessages = new PendingMessages(groupName, List.of(msg));
        when(streamOps.pending(dmStreamKey, groupName, Range.unbounded(), 100L))
                .thenReturn(pendingMessages);

        // when
        scheduler.recoverDmMessages();

        // then: 아무런 동작(Claim, Ack, Send)도 일어나지 않아야 함
        verify(streamOps, never()).acknowledge(any(), any(), any(RecordId.class));
        verify(streamOps, never()).claim(any(), any(), any(), any());
    }
}
