package codeit.sb06.otboo.message.publisher;

import codeit.sb06.otboo.config.RedisStreamProperties;
import codeit.sb06.otboo.exception.message.DirectMessageMappingException;
import codeit.sb06.otboo.message.dto.DirectMessageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
@Component
@RequiredArgsConstructor
public class DirectMessageRedisPublisher {

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

            // XADD와 길이 제한을 한 명령으로 실행한다. EXPIRE는 Consumer Group까지 삭제하므로 설정하지 않는다.
            XAddOptions options = XAddOptions.maxlen(streamProperties.maxLength())
                    .approximateTrimming(true);
            addWithOneRetry(record, options);
        } catch (JsonProcessingException e) {
            throw new DirectMessageMappingException();
        }
    }

    private void addWithOneRetry(
            MapRecord<String, String, String> record,
            XAddOptions options) {
        try {
            redisTemplate.opsForStream().add(record, options);
        } catch (DataAccessException exception) {
            redisTemplate.opsForStream().add(record, options);
        }
    }
}
