package codeit.sb06.otboo.message.listener;

import codeit.sb06.otboo.config.RedisStreamProperties;
import codeit.sb06.otboo.message.dto.DirectMessageRedisDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectMessageRedisStreamListenerTest {

    private static final String STREAM_KEY = "dm:stream";
    private static final String SERVER_ID = "node-1";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private StreamOperations<String, String, String> streamOperations;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private DirectMessageStreamListener listener;

    @BeforeEach
    void setUp() {
        // 생성자 주입 (Lombok @RequiredArgsConstructor 대응)
        RedisStreamProperties streamProperties =
                new RedisStreamProperties("notification:stream", STREAM_KEY);
        listener = new DirectMessageStreamListener(
                redisTemplate, streamProperties, SERVER_ID, messagingTemplate
        );
    }

    @Test
    @DisplayName("성공: 메시지 수신 시 JSON 파싱 -> 전송 -> ACK 과정이 완벽해야 한다")
    void onMessage_Success() throws Exception {
        RecordId recordId = RecordId.of("1000-0");
        String destination = "/sub/chat/room1";
        String jsonPayload = objectMapper.writeValueAsString(new DirectMessageRedisDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Hello, World!",
                destination
        ));

        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .in(STREAM_KEY)
                .ofMap(Map.of("payload", jsonPayload, "destination", destination))
                .withId(recordId);

        doReturn(streamOperations).when(redisTemplate).opsForStream();

        // 4. 리스너 실행
        listener.onMessage(record);

        // 5. 검증 (Verify)
        verify(messagingTemplate).convertAndSend(destination, jsonPayload);
        verify(streamOperations).acknowledge(anyString(), anyString(), eq(recordId));
    }
}
