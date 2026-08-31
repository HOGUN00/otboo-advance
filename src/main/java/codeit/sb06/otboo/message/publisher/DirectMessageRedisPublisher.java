package codeit.sb06.otboo.message.publisher;

import codeit.sb06.otboo.config.RedisStreamProperties;
import codeit.sb06.otboo.exception.message.DirectMessageMappingException;
import codeit.sb06.otboo.message.dto.DirectMessageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class DirectMessageRedisPublisher {

    public static final int TIMEOUT = 1;
    public static final int COUNT = 30000;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisStreamProperties streamProperties;

    public void publish(DirectMessageDto dto, String destination) {

        try {
            String dmStreamKey = streamProperties.directMessageKey();
            String jsonPayload = objectMapper.writeValueAsString(dto);
            Map<String, String> map = Map.of(
                    "payload", jsonPayload,
                    "destination", destination,
                    "receiverId", dto.receiver().userId().toString()
            );

            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .in(dmStreamKey)
                    .ofMap(map)
                    .withId(RecordId.autoGenerate());

            redisTemplate.opsForStream().add(record);
            redisTemplate.opsForStream().trim(dmStreamKey, COUNT, true);
            redisTemplate.expire(dmStreamKey, TIMEOUT, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            throw new DirectMessageMappingException();
        }
    }
}
