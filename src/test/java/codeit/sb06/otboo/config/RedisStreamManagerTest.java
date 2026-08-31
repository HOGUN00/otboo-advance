package codeit.sb06.otboo.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisStreamManagerTest {

    private static final String SERVER_ID = "server-1";
    private static final String NOTIFICATION_STREAM_KEY = "notification:stream";
    private static final String DM_STREAM_KEY = "direct-message:stream";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private StreamOperations<String, String, String> streamOperations;

    private RedisStreamManager streamManager;

    @BeforeEach
    void setUp() {
        RedisStreamProperties streamProperties =
                new RedisStreamProperties(NOTIFICATION_STREAM_KEY, DM_STREAM_KEY);
        streamManager = new RedisStreamManager(redisTemplate, SERVER_ID, streamProperties);
        doReturn(streamOperations).when(redisTemplate).opsForStream();
    }

    @Test
    @DisplayName("초기화 메시지 없이 스트림과 컨슈머 그룹을 생성한다")
    void createsStreamsAndGroupsWithoutInitRecords() {
        streamManager.init();

        verify(streamOperations).createGroup(
                NOTIFICATION_STREAM_KEY, ReadOffset.latest(), "group-noti-" + SERVER_ID);
        verify(streamOperations).createGroup(
                DM_STREAM_KEY, ReadOffset.latest(), "group-dm-" + SERVER_ID);
        verifyNoMoreInteractions(streamOperations);
    }

    @Test
    @DisplayName("컨슈머 그룹이 이미 존재하면 BUSYGROUP 예외를 무시한다")
    void ignoresAlreadyExistingGroups() {
        when(streamOperations.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenThrow(new RedisSystemException(
                        "BUSYGROUP Consumer Group name already exists", new RuntimeException()));

        assertDoesNotThrow(streamManager::init);
    }
}
