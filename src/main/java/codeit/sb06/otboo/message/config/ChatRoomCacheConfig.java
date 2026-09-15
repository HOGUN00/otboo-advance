package codeit.sb06.otboo.message.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@EnableConfigurationProperties(ChatRoomCacheProperties.class)
public class ChatRoomCacheConfig {

    @Bean
    public CacheManager cacheManager(ChatRoomCacheProperties properties) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("chatRoomIds");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterAccess(properties.expireAfterAccess())
                .recordStats());
        // ChatRoom 생성 트랜잭션이 롤백될 경우 잘못된 ID가 캐시에 남지 않도록,
        // Cache 변경은 트랜잭션 커밋 이후에 반영한다.
        return new TransactionAwareCacheManagerProxy(cacheManager);
    }
}
