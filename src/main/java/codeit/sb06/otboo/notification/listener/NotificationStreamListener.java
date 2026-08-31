package codeit.sb06.otboo.notification.listener;


import codeit.sb06.otboo.config.RedisStreamProperties;
import codeit.sb06.otboo.notification.service.SseService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final StringRedisTemplate redisTemplate;
    private final RedisStreamProperties streamProperties;
    private final String serverId;
    private final SseService sseService;

    @PostConstruct
    public void debug() {
        log.debug(">>>> [리스너 체크] 그룹명: group-noti-{}", serverId);
        log.debug(">>>> [리스너 체크] 스트림 키: {}", streamProperties.notificationKey());
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {

        String notificationStreamKey = streamProperties.notificationKey();
        log.debug("알림 수신: [MessageId: {}, Stream: {}]", record.getId(), notificationStreamKey);

        try {
            String json = record.getValue().get("payload");
            String receiverId = record.getValue().get("receiverId");

            sseService.send(UUID.fromString(receiverId), "notifications", json);

            redisTemplate.opsForStream().acknowledge(notificationStreamKey, "group-noti-" + serverId, record.getId());
            log.debug("알림 전송 및 ACK 완료: [MessageId: {}, ReceiverId: {}]", record.getId(), receiverId);

        } catch (Exception e) {
            log.error("알림 처리 실패: [MessageId: {}]", record.getId(), e);
            log.error("실패한 원본 데이터: {}", record.getValue());
        }
    }
}
