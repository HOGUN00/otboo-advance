package codeit.sb06.otboo.message.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "otboo.cache.chat-room")
public record ChatRoomCacheProperties(
        @Positive long maximumSize,
        @NotNull Duration expireAfterAccess
) {
}
