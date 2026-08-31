package codeit.sb06.otboo.message.publisher;

import codeit.sb06.otboo.config.RedisStreamProperties;
import codeit.sb06.otboo.message.dto.DirectMessageDto;
import codeit.sb06.otboo.util.EasyRandomUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectMessageRedisPublisherTest {

    private static final String DM_STREAM_KEY = "dm:stream";

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, String, String> streamOps;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private DirectMessageRedisPublisher directMessageRedisPublisher;

    @BeforeEach
    void setUp() {
        RedisStreamProperties streamProperties =
                new RedisStreamProperties("notification:stream", DM_STREAM_KEY);
        directMessageRedisPublisher = new DirectMessageRedisPublisher(redisTemplate, objectMapper, streamProperties);
        doReturn(streamOps).when(redisTemplate).opsForStream();
    }

    @Test
    @DisplayName("DM이 Redis 스트림에 발행된다.")
    void publishDirectMessageTest() {
        // given
        DirectMessageDto dto = easyRandom.nextObject(DirectMessageDto.class);

        // when
        directMessageRedisPublisher.publish(dto, "destination");

        // then
        verify(streamOps, times(1)).add(any());
    }
}
