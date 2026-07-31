package codeit.sb06.otboo.notification.service;

import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.repository.SseEmitterRepository;
import codeit.sb06.otboo.notification.service.impl.SseServiceImpl;
import codeit.sb06.otboo.notification.util.SseEventIdGenerator;
import codeit.sb06.otboo.util.EasyRandomUtil;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SseServiceTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();
    private final UUID userId = UUID.randomUUID();
    @Mock
    private SseEmitterRepository sseEmitterRepository;
    @Mock
    private NotificationCacheService notificationCacheService;
    @Spy
    private SseEventIdGenerator sseEventIdGenerator;
    @InjectMocks
    private SseServiceImpl sseService;

    @Test
    @DisplayName("sseEmitter를 저장하고 반환한다.")
    void subscribeTest() {
        // given
        given(sseEmitterRepository.save(eq(userId), any(SseEmitter.class)))
                .willAnswer(invocation -> invocation.getArgument(1));

        // when
        SseEmitter emitter = sseService.subscribe(userId, "");

        // then
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("last event id가 존재할 때 캐시 서비스에서 이후 알림을 조회한다.")
    void subscribeWithLastEventIdTest() {
        // given
        String lastEventId = easyRandom.nextObject(String.class);
        NotificationDto mockDto = easyRandom.nextObject(NotificationDto.class);
        List<NotificationDto> dtoList = List.of(mockDto);

        given(sseEmitterRepository.save(eq(userId), any(SseEmitter.class)))
                .willAnswer(invocation -> invocation.getArgument(1));
        given(notificationCacheService.getNotificationsAfter(userId, lastEventId))
                .willReturn(dtoList);

        // when
        sseService.subscribe(userId, lastEventId);

        // then
        verify(notificationCacheService, times(1))
                .getNotificationsAfter(userId, lastEventId);
    }

    @Test
    @DisplayName("연결 끊긴 후 다시 연결 시 기존 emitter를 삭제한다.")
    void subscribeWhenAlreadyExistsTest() {
        // given
        SseEmitter existingEmitter = mock(SseEmitter.class);
        given(sseEmitterRepository.findById(userId)).willReturn(existingEmitter);
        given(sseEmitterRepository.save(eq(userId), any(SseEmitter.class)))
                .willAnswer(invocation -> invocation.getArgument(1));

        // when
        SseEmitter newEmitter = sseService.subscribe(userId, "");

        // then
        assertThat(newEmitter).isNotNull();
        verify(sseEmitterRepository, times(1)).deleteIfMatches(userId, existingEmitter);
    }

    @Test
    @DisplayName("sse 이벤트 전송을 테스트한다.")
    void sendTest() {
        // given
        SseEmitter mockEmitter = mock(SseEmitter.class);
        given(sseEmitterRepository.findById(userId)).willReturn(mockEmitter);

        // when
        sseService.send(userId, "testEvent", "testData");

        // then
        try {
            verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
