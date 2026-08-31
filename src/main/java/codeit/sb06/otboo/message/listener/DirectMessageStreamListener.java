package codeit.sb06.otboo.message.listener;

import codeit.sb06.otboo.config.RedisStreamProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectMessageStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final StringRedisTemplate redisTemplate;
    private final RedisStreamProperties streamProperties;
    private final String serverId;
    private final SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void debug() {
        log.debug(">>>> [리스너 체크] 그룹명: group-dm-{}", serverId);
        log.debug(">>>> [리스너 체크] 스트림 키: {}", streamProperties.directMessageKey());
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {

        String dmStreamKey = streamProperties.directMessageKey();
        log.debug("dm 수신: [MessageId: {}, Stream: {}]", record.getId(), dmStreamKey);

        try {
            String json = record.getValue().get("payload");
            String destination = record.getValue().get("destination");
            String receiverId = record.getValue().get("receiverId");

            messagingTemplate.convertAndSend(destination, json);

            redisTemplate.opsForStream().acknowledge(dmStreamKey, "group-dm-" + serverId, record.getId());
            log.debug("dm 전송 및 ACK 완료: [MessageId: {}, ReceiverId: {}]", record.getId(), receiverId);

        } catch (Exception e) {
            log.error("dm 처리 실패: [MessageId: {}], 오류: {}", record.getId(), e.getMessage());
            log.error("실패한 원본 데이터: {}", record.getValue());
        }
    }
}
