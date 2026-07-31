package codeit.sb06.otboo.notification.scheduler;

import codeit.sb06.otboo.notification.repository.SseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SseHeartbeatSchedulerTest {

    @Mock
    private SseEmitterRepository sseEmitterRepository;

    @InjectMocks
    private SseHeartbeatScheduler sseHeartbeatScheduler;

    @Test
    @DisplayName("하트비트 전송 성공 - 예외 없이 정상적으로 ping 데이터를 보낸다")
    void sendHeartbeat_Success() throws Exception {
        // given
        UUID emitterId = UUID.randomUUID();
        SseEmitter successEmitter = mock(SseEmitter.class);

        given(sseEmitterRepository.findAll()).willReturn(Map.of(emitterId, successEmitter));

        // when
        sseHeartbeatScheduler.sendHeartbeat();

        // then
        verify(successEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(successEmitter, never()).complete();
        verify(sseEmitterRepository, never()).deleteIfMatches(any(), any());
    }

    @Test
    @DisplayName("하트비트 전송 실패 - 예외 발생 시 Emitter를 완료 처리하고 저장소에서 삭제한다")
    void sendHeartbeat_Failure() throws Exception {
        // given
        UUID emitterId = UUID.randomUUID();
        SseEmitter failEmitter = mock(SseEmitter.class);

        // send 호출 시 예외가 발생하도록 설정
        doThrow(new RuntimeException("전송 실패 테스트")).when(failEmitter).send(any(SseEmitter.SseEventBuilder.class));

        given(sseEmitterRepository.findAll()).willReturn(Map.of(emitterId, failEmitter));

        // when
        sseHeartbeatScheduler.sendHeartbeat();

        // then
        verify(failEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(failEmitter, times(1)).complete();
        verify(sseEmitterRepository, times(1)).deleteIfMatches(emitterId, failEmitter);
    }
}
