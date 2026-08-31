package codeit.sb06.otboo.notification.scheduler;

import codeit.sb06.otboo.common.scheduler.AbstractStreamRecoveryScheduler;
import codeit.sb06.otboo.config.RedisStreamProperties;
import codeit.sb06.otboo.notification.service.SseService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class NotificationStreamRecoveryScheduler extends AbstractStreamRecoveryScheduler {

    private final SseService sseService;
    private final RedisStreamProperties streamProperties;

    public NotificationStreamRecoveryScheduler(
            StringRedisTemplate redisTemplate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            String serverId,
            RedisStreamProperties streamProperties,
            SseService sseService) {

        super(redisTemplate, circuitBreakerRegistry, serverId);
        this.streamProperties = streamProperties;
        this.sseService = sseService;
    }

    @Override
    protected String getStreamKey() {
        return streamProperties.notificationKey();
    }

    @Override
    protected String getCircuitBreakerName() {
        return "notificationStreamCircuit";
    }

    @Override
    protected String getStreamNameForLog() {
        return "알림";
    }

    @Override
    protected Duration getMinIdleTime() {
        return Duration.ofMinutes(1);
    }

    @Override
    protected String getGroupNamePrefix() {
        return "group-noti-";
    }

    @Override
    protected String getWorkerNamePrefix() {
        return "notification-recover-worker-";
    }

    @Override
    protected void processClaimedRecord(MapRecord<String, String, String> record) {
        String json = record.getValue().get("payload");
        String receiverId = record.getValue().get("receiverId");
        sseService.send(UUID.fromString(receiverId), "notifications", json);
    }

    @Scheduled(fixedDelay = 20000, initialDelayString = "${scheduler.initial-delay.noti}")
    @CircuitBreaker(name = "notificationStreamCircuit", fallbackMethod = "fallbackRecover")
    public void recoverNotificationMessages() {
        super.doRecover();
    }

    public void fallbackRecover(Exception e) {
        super.doFallbackRecover(e);
    }
}
