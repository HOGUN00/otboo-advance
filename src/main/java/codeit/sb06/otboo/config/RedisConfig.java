package codeit.sb06.otboo.config;

import codeit.sb06.otboo.message.listener.DirectMessageStreamListener;
import codeit.sb06.otboo.notification.listener.NotificationStreamListener;
import io.lettuce.core.RedisCommandExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Configuration
@EnableConfigurationProperties(RedisStreamProperties.class)
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public String serverId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("호스트 이름을 가져오는 데 실패하여 랜덤 UUID를 사용", e);
            return UUID.randomUUID().toString().substring(0, 8);
        }
    }

    @Bean
    @DependsOn("redisStreamManager")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> container(
            RedisConnectionFactory connectionFactory,
            NotificationStreamListener notificationStreamListener,
            DirectMessageStreamListener dmStreamListener,
            RedisStreamProperties streamProperties,
            String serverId) {

        var options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .errorHandler(t -> {
                            if (t instanceof RedisSystemException &&
                                t.getCause() instanceof RedisCommandExecutionException &&
                                t.getCause().getMessage().contains("NOGROUP")
                            ) {
                                log.debug("[Redis Stream] 소비자 그룹이 존재하지 않아 폴링을 종료합니다.");
                                return;
                            }
                            log.error("[Redis Stream Error] ", t);
                        })
                        .build();

        var container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(
                Consumer.from("group-noti-" + serverId, "instance-" + serverId),
                StreamOffset.create(streamProperties.notificationKey(), ReadOffset.lastConsumed()),
                notificationStreamListener
        );

        container.receive(
                Consumer.from("group-dm-" + serverId, "instance-" + serverId),
                StreamOffset.create(streamProperties.directMessageKey(), ReadOffset.lastConsumed()),
                dmStreamListener
        );

        container.start();

        log.debug("[리스너 시작] 그룹명: group-noti-{}, 스트림 키: {}, {}", serverId,
                streamProperties.notificationKey(), streamProperties.directMessageKey());

        return container;
    }
}
