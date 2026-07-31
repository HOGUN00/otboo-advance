package codeit.sb06.otboo.notification.scheduler;

import codeit.sb06.otboo.notification.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final SseEmitterRepository sseEmitterRepository;

    @Scheduled(fixedDelay = 15000)
    public void sendHeartbeat() {

        Map<UUID, SseEmitter> allEmitters = sseEmitterRepository.findAll();

        for (Map.Entry<UUID, SseEmitter> entry : allEmitters.entrySet()) {
            SseEmitter emitter = entry.getValue();
            try {
                emitter.send(SseEmitter.event().name("ping").data("heartbeat"));
            } catch (Exception e) {
                emitter.complete();
                sseEmitterRepository.deleteIfMatches(entry.getKey(), emitter);
            }
        }
    }
}
