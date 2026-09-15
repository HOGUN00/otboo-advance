package codeit.sb06.otboo.notification.publisher;

import codeit.sb06.otboo.config.RedisStreamProperties;
import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.publisher.impl.RedisNotificationPublisherImpl;
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
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisNotificationPublisherTest {

    private static final String NOTIFICATION_STREAM_KEY = "notification:stream";
    private static final long STREAM_MAX_LENGTH = 12_345L;

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, String, String> streamOps;

    @Mock
    private RedisOperations<String, String> redisOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private RedisNotificationPublisherImpl redisNotificationPublisher;

    @BeforeEach
    void setUp() {
        RedisStreamProperties streamProperties =
                new RedisStreamProperties(
                        NOTIFICATION_STREAM_KEY,
                        "direct-message:stream",
                        STREAM_MAX_LENGTH
        );
        redisNotificationPublisher = new RedisNotificationPublisherImpl(redisTemplate, objectMapper, streamProperties);
    }


    @Test
    @DisplayName("알림을 발행하면서 MAXLEN 근사 방식으로 Stream 길이를 제한한다.")
    void publishNotificationTest() {
        // given
        NotificationDto dto = easyRandom.nextObject(NotificationDto.class);
        willReturn(streamOps).given(redisTemplate).opsForStream();

        // when
        redisNotificationPublisher.publish(dto);

        // then
        ArgumentCaptor<XAddOptions> optionsCaptor = ArgumentCaptor.forClass(XAddOptions.class);
        verify(streamOps).add(any(MapRecord.class), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getMaxlen()).isEqualTo(STREAM_MAX_LENGTH);
        assertThat(optionsCaptor.getValue().isApproximateTrimming()).isTrue();
        verify(streamOps, never()).trim(anyString(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("알림 목록도 각각 MAXLEN 근사 방식으로 Stream 길이를 제한한다.")
    @SuppressWarnings("unchecked")
    void publishAllNotificationsTest() {
        // given
        List<NotificationDto> dtoList = easyRandom.objects(NotificationDto.class, 2).toList();
        willReturn(streamOps).given(redisOperations).opsForStream();

        // when
        redisNotificationPublisher.publishAll(dtoList);

        // then
        ArgumentCaptor<SessionCallback<Object>> callbackCaptor =
                ArgumentCaptor.forClass(SessionCallback.class);
        verify(redisTemplate).executePipelined(callbackCaptor.capture());
        callbackCaptor.getValue().execute(redisOperations);

        ArgumentCaptor<XAddOptions> optionsCaptor = ArgumentCaptor.forClass(XAddOptions.class);
        verify(streamOps, times(2)).add(any(MapRecord.class), optionsCaptor.capture());
        assertThat(optionsCaptor.getAllValues())
                .allSatisfy(options -> {
                    assertThat(options.getMaxlen()).isEqualTo(STREAM_MAX_LENGTH);
                    assertThat(options.isApproximateTrimming()).isTrue();
                });
        verify(streamOps, never()).trim(anyString(), anyLong(), anyBoolean());
    }
}
