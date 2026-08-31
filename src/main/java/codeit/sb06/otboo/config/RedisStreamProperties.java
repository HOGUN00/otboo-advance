package codeit.sb06.otboo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otboo.redis.stream")
public record RedisStreamProperties(
        String notificationKey,
        String directMessageKey
) {
}
