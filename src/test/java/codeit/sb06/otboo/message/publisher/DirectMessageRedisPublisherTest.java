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
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DirectMessageRedisPublisherTest {

    private static final String DM_STREAM_KEY = "dm:stream";
    private static final long STREAM_MAX_LENGTH = 12_345L;

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
                new RedisStreamProperties("notification:stream", DM_STREAM_KEY, STREAM_MAX_LENGTH);
        directMessageRedisPublisher = new DirectMessageRedisPublisher(redisTemplate, objectMapper, streamProperties);
        willReturn(streamOps).given(redisTemplate).opsForStream();
    }

    @Test
    @DisplayName("DM을 발행하면서 MAXLEN 근사 방식으로 Stream 길이를 제한한다.")
    void publishDirectMessageTest() {
        // given
        DirectMessageDto dto = easyRandom.nextObject(DirectMessageDto.class);

        // when
        directMessageRedisPublisher.publish(dto, "destination");

        // then
        ArgumentCaptor<XAddOptions> optionsCaptor = ArgumentCaptor.forClass(XAddOptions.class);
        verify(streamOps).add(any(MapRecord.class), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getMaxlen()).isEqualTo(STREAM_MAX_LENGTH);
        assertThat(optionsCaptor.getValue().isApproximateTrimming()).isTrue();
        verify(streamOps, never()).trim(anyString(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("DM XADD가 처음 실패하면 한 번 재시도한다.")
    void retryXaddOnceWhenFirstAttemptFails() {
        // given
        DirectMessageDto dto = easyRandom.nextObject(DirectMessageDto.class);
        RedisSystemException exception = new RedisSystemException(
                "일시적인 Redis 오류",
                new RuntimeException());
        given(streamOps.add(any(MapRecord.class), any(XAddOptions.class)))
                .willThrow(exception)
                .willReturn(RecordId.of("1-0"));

        // when
        directMessageRedisPublisher.publish(dto, "destination");

        // then
        verify(streamOps, times(2)).add(any(MapRecord.class), any(XAddOptions.class));
        verify(streamOps, never()).trim(anyString(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("DM XADD가 두 번 실패하면 더 재시도하지 않고 예외를 전파한다.")
    void propagateWhenBothXaddAttemptsFail() {
        // given
        DirectMessageDto dto = easyRandom.nextObject(DirectMessageDto.class);
        RedisSystemException exception = redisFailure();
        given(streamOps.add(any(MapRecord.class), any(XAddOptions.class))).willThrow(exception);

        // when & then
        assertThatThrownBy(() -> directMessageRedisPublisher.publish(dto, "destination"))
                .isSameAs(exception);
        verify(streamOps, times(2)).add(any(MapRecord.class), any(XAddOptions.class));
        verify(streamOps, never()).trim(anyString(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("DataAccessException이 아닌 XADD 실패는 재시도하지 않는다.")
    void doNotRetryNonDataAccessException() {
        // given
        DirectMessageDto dto = easyRandom.nextObject(DirectMessageDto.class);
        IllegalStateException exception = new IllegalStateException("재시도 불가 오류");
        given(streamOps.add(any(MapRecord.class), any(XAddOptions.class))).willThrow(exception);

        // when & then
        assertThatThrownBy(() -> directMessageRedisPublisher.publish(dto, "destination"))
                .isSameAs(exception);
        verify(streamOps).add(any(MapRecord.class), any(XAddOptions.class));
    }

    private RedisSystemException redisFailure() {
        return new RedisSystemException("일시적인 Redis 오류", new RuntimeException());
    }
}
