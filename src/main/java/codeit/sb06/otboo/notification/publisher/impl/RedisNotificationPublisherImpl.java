package codeit.sb06.otboo.notification.publisher.impl;

import codeit.sb06.otboo.config.RedisStreamProperties;
import codeit.sb06.otboo.exception.notification.NotificationMappingException;
import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.publisher.RedisNotificationPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisNotificationPublisherImpl implements RedisNotificationPublisher {

    public static final int TIMEOUT = 1;
    public static final int COUNT = 30000;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisStreamProperties streamProperties;

    @Override
    public void publish(NotificationDto dto) {

        try {
            String notificationStreamKey = streamProperties.notificationKey();
            String jsonPayload = objectMapper.writeValueAsString(dto);
            Map<String, String> map = Map.of(
                    "payload", jsonPayload,
                    "receiverId", dto.receiverId().toString());

            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .in(notificationStreamKey)
                    .ofMap(map)
                    .withId(RecordId.autoGenerate());

            redisTemplate.opsForStream().add(record);
            redisTemplate.opsForStream().trim(notificationStreamKey, COUNT, true);
            redisTemplate.expire(notificationStreamKey, TIMEOUT, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            throw new NotificationMappingException();
        }
    }

    @Override
    public void publishAll(List<NotificationDto> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) return;

        String notificationStreamKey = streamProperties.notificationKey();
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(@NonNull RedisOperations operations) {
                for (NotificationDto dto : dtoList) {

                    String jsonPayload = null;
                    try {
                        jsonPayload = objectMapper.writeValueAsString(dto);
                    } catch (JsonProcessingException e) {
                        throw new NotificationMappingException();
                    }
                    Map<String, String> map = Map.of(
                            "payload", jsonPayload,
                            "receiverId", dto.receiverId().toString());

                    MapRecord<String, String, String> record = StreamRecords.newRecord()
                            .in(notificationStreamKey)
                            .ofMap(map)
                            .withId(RecordId.autoGenerate());

                    operations.opsForStream().add(record);
                }

                operations.opsForStream().trim(notificationStreamKey, COUNT, true);
                operations.expire(notificationStreamKey, TIMEOUT, TimeUnit.DAYS);

                return null;
            }
        });
    }
}
