package codeit.sb06.otboo.message.config;

import com.github.benmanes.caffeine.cache.Cache;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomCacheConfigTest {

    private final ChatRoomCacheProperties properties = new ChatRoomCacheProperties(
            100_000,
            Duration.ofHours(1));
    private final ChatRoomCacheConfig config = new ChatRoomCacheConfig();

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("ChatRoom ID Cache에 최대 크기와 미사용 만료 정책을 적용한다.")
    @SuppressWarnings("unchecked")
    void createChatRoomIdCacheWithConfiguredPolicy() {
        // when
        CacheManager cacheManager = config.cacheManager(properties);
        org.springframework.cache.Cache springCache = cacheManager.getCache("chatRoomIds");
        Cache<String, UUID> cache = (Cache<String, UUID>) springCache.getNativeCache();

        // then
        assertThat(cache.policy().eviction().orElseThrow().getMaximum())
                .isEqualTo(100_000);
        assertThat(cache.policy().expireAfterAccess().orElseThrow()
                .getExpiresAfter(TimeUnit.HOURS))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Transaction이 Commit된 후 Cache에 값을 등록한다.")
    void deferCachePutUntilAfterCommit() {
        // given
        CacheManager cacheManager = config.cacheManager(properties);
        org.springframework.cache.Cache cache = cacheManager.getCache("chatRoomIds");
        String dmKey = "sender_receiver";
        UUID chatRoomId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();

        // when
        cache.put(dmKey, chatRoomId);

        // then
        assertThat(cache.get(dmKey)).isNull();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(cache.get(dmKey, UUID.class)).isEqualTo(chatRoomId);
    }

    @Test
    @DisplayName("Transaction이 Rollback되면 Cache에 값을 등록하지 않는다.")
    void discardCachePutAfterRollback() {
        // given
        CacheManager cacheManager = config.cacheManager(properties);
        org.springframework.cache.Cache cache = cacheManager.getCache("chatRoomIds");
        String dmKey = "sender_receiver";
        UUID chatRoomId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();

        // when
        cache.put(dmKey, chatRoomId);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));

        // then
        assertThat(cache.get(dmKey)).isNull();
    }
}
