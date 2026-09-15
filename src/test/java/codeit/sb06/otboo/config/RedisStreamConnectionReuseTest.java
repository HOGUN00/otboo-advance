package codeit.sb06.otboo.config;

import codeit.sb06.otboo.notification.config.EmbeddedRedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Import(EmbeddedRedisConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.data.redis.port=16380")
class RedisStreamConnectionReuseTest {

    private static final String STREAM_KEY = "test:connection-reuse:stream";
    private static final String GROUP_NAME = "test-connection-reuse-group";
    private static final int MESSAGE_COUNT = 200;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Value("${spring.data.redis.lettuce.pool.max-active}")
    private int maxActiveConnections;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private Subscription subscription;

    @BeforeEach
    void setUp() throws InterruptedException {
        redisTemplate.opsForStream().add(STREAM_KEY, Map.of("bootstrap", "true"));
        redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.latest(), GROUP_NAME);

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofMillis(100))
                .build();
        container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (subscription != null) {
            subscription.cancel();
        }
        if (container != null) {
            CountDownLatch stopped = new CountDownLatch(1);
            container.stop(stopped::countDown);
            assertThat(stopped.await(2, TimeUnit.SECONDS)).isTrue();
        }
        redisTemplate.delete(STREAM_KEY);
    }

    @Test
    @DisplayName("메시지 200건을 소비할 때 새 Redis 물리 연결 수는 pool 최대 크기 이하이다")
    void reusesPhysicalConnectionWhilePollingMessages() throws InterruptedException {
        BlockingQueue<RecordId> consumed = new LinkedBlockingQueue<>();
        subscription = container.receive(
                Consumer.from(GROUP_NAME, "test-consumer"),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                message -> consumed.offer(message.getId())
        );
        assertThat(subscription.await(Duration.ofSeconds(2))).isTrue();

        long connectionsBefore = totalConnectionsReceived();

        for (int index = 0; index < MESSAGE_COUNT; index++) {
            RecordId sent = redisTemplate.opsForStream().add(
                    STREAM_KEY,
                    Map.of("sequence", Integer.toString(index))
            );
            assertThat(consumed.poll(2, TimeUnit.SECONDS)).isEqualTo(sent);
        }

        long newPhysicalConnections = totalConnectionsReceived() - connectionsBefore;

        assertThat(newPhysicalConnections)
                .as("메시지 200건 소비 중 새로 생성된 Redis 물리 연결 수")
                .isLessThanOrEqualTo(maxActiveConnections);
    }

    private long totalConnectionsReceived() {
        RedisCallback<Properties> readStats =
                connection -> connection.serverCommands().info("stats");
        Properties stats = redisTemplate.execute(readStats);
        assertThat(stats).isNotNull();
        return Long.parseLong(stats.getProperty("total_connections_received"));
    }
}
