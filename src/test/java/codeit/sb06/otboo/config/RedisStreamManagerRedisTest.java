package codeit.sb06.otboo.config;

import codeit.sb06.otboo.notification.config.EmbeddedRedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataRedisTest
@Import(EmbeddedRedisConfig.class)
@ActiveProfiles("test")
class RedisStreamManagerRedisTest {

    private static final String NOTIFICATION_STREAM_KEY = "test:notification:stream";
    private static final String DM_STREAM_KEY = "test:direct-message:stream";
    private static final String SERVER_ID = "test-server";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(NOTIFICATION_STREAM_KEY);
        redisTemplate.delete(DM_STREAM_KEY);
    }

    @Test
    @DisplayName("스트림이 없으면 빈 스트림과 컨슈머 그룹을 함께 생성한다")
    void createsEmptyStreamsAndGroupsWhenStreamsDoNotExist() {
        RedisStreamProperties streamProperties =
                new RedisStreamProperties(NOTIFICATION_STREAM_KEY, DM_STREAM_KEY);
        RedisStreamManager streamManager =
                new RedisStreamManager(redisTemplate, SERVER_ID, streamProperties);

        streamManager.init();

        assertTrue(redisTemplate.hasKey(NOTIFICATION_STREAM_KEY));
        assertTrue(redisTemplate.hasKey(DM_STREAM_KEY));
        assertEquals(0L, redisTemplate.opsForStream().size(NOTIFICATION_STREAM_KEY));
        assertEquals(0L, redisTemplate.opsForStream().size(DM_STREAM_KEY));
        assertDoesNotThrow(() -> redisTemplate.opsForStream()
                .pending(NOTIFICATION_STREAM_KEY, "group-noti-" + SERVER_ID));
        assertDoesNotThrow(() -> redisTemplate.opsForStream()
                .pending(DM_STREAM_KEY, "group-dm-" + SERVER_ID));
    }
}
