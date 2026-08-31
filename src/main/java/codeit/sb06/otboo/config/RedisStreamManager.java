package codeit.sb06.otboo.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamManager {

    private final StringRedisTemplate redisTemplate;
    private final String serverId;
    private final RedisStreamProperties streamProperties;

    @PostConstruct
    public void init() {
        createStreamAndGroup(streamProperties.notificationKey(), "group-noti-" + serverId);
        createStreamAndGroup(streamProperties.directMessageKey(), "group-dm-" + serverId);
    }

    private void createStreamAndGroup(String streamKey, String groupName) {
        try {
            // 스트림이 없으면 생성
            if (!redisTemplate.hasKey(streamKey)) {
                RecordId id = redisTemplate.opsForStream().add(streamKey, Map.of("_init", "true"));
                log.debug("[Redis Stream] 스트림 생성 완료: {}", streamKey);
                if (id != null) {
                    redisTemplate.opsForStream().delete(streamKey, id);
                }
            }
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), groupName);
            log.debug("[Redis Stream] 소비자 그룹 생성 성공: {}", groupName);

        } catch (RedisSystemException e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.info("[Redis Stream] 소비자 그룹이 이미 존재합니다: {}", groupName);
            } else {
                log.error("[Redis Stream] 소비자 그룹 생성 중 예외 발생: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("[Redis Stream] 알 수 없는 오류 발생: {}", e.getMessage());
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void destroyOnShutdown() {
        try {
            redisTemplate.opsForStream().destroyGroup(streamProperties.notificationKey(), "group-noti-" + serverId);
            redisTemplate.opsForStream().destroyGroup(streamProperties.directMessageKey(), "group-dm-" + serverId);
            log.debug("[Redis Stream] 서버 종료로 인한 소비자 그룹 삭제 완료");
        } catch (Exception e) {
            log.warn("[Redis Stream] 소비자 그룹 삭제 중 예외 발생: {}", e.getMessage());
        }
    }
}
