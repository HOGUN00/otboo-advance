package codeit.sb06.otboo.notification.service.impl;

import codeit.sb06.otboo.notification.dto.SseEvent;
import codeit.sb06.otboo.notification.repository.SseEmitterRepository;
import codeit.sb06.otboo.notification.service.NotificationCacheService;
import codeit.sb06.otboo.notification.service.SseService;
import codeit.sb06.otboo.notification.util.SseEventIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseServiceImpl implements SseService {

    public static final long TIMEOUT = 60 * 1000L * 60; // 1 hour
    private final SseEmitterRepository sseEmitterRepository;
    private final NotificationCacheService notificationCacheService;
    private final SseEventIdGenerator sseEventIdGenerator;

    @Override
    public SseEmitter subscribe(UUID userId, String lastEventId) {

        SseEmitter oldEmitter = sseEmitterRepository.findById(userId);
        if (oldEmitter != null) {
            oldEmitter.complete();
            sseEmitterRepository.deleteIfMatches(userId, oldEmitter);
        }

        SseEmitter emitter = new SseEmitter(TIMEOUT);

        configEmitter(emitter, userId);

        String eventId = sseEventIdGenerator.generator(null, userId);
        sendToClient(
                emitter,
                userId,
                SseEvent.of(eventId, "subscribe", "EventStream Created. [userId=" + userId + "]")
        );

        sseEmitterRepository.save(userId, emitter);

        if (lastEventId != null && !lastEventId.isEmpty()) {
            notificationCacheService.getNotificationsAfter(userId, lastEventId)
                    .forEach(notificationDto ->
                            sendToClient(emitter, userId,
                                    SseEvent.of(
                                            sseEventIdGenerator.generator(notificationDto.createdAt(), userId),
                                            "notifications",
                                            notificationDto
                                    ))
                    );
        }

        return emitter;
    }

    @Override
    public void send(UUID userId, String eventName, Object data) {

        String eventId = sseEventIdGenerator.generator(null, userId);
        SseEvent event = SseEvent.of(eventId, eventName, data);

        SseEmitter emitter = sseEmitterRepository.findById(userId);
        if (emitter != null) {
            sendToClient(emitter, userId, event);
        }
    }

    private void sendToClient(SseEmitter emitter, UUID userId, SseEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(event.id())
                    .name(event.name())
                    .data(event.data()));
            log.debug("SSE Event Sent. [userId={}, eventId={}, eventName={}, data={}]",
                    userId, event.id(), event.name(), event.data());
        } catch (IOException e) {
            sseEmitterRepository.deleteIfMatches(userId, emitter);
        }
    }

    private void configEmitter(SseEmitter emitter, UUID userId) {

        emitter.onCompletion(() -> sseEmitterRepository.deleteIfMatches(userId, emitter));
        emitter.onTimeout(() -> {
            sseEmitterRepository.deleteIfMatches(userId, emitter);
            log.error("SSE Emitter Timeout. [userId={}]", userId);
        });
        emitter.onError(e -> {
            emitter.complete();
            log.error("SSE Emitter Error. [userId={}]", userId, e);
        });
    }
}
