package codeit.sb06.otboo.notification.service.impl;

import codeit.sb06.otboo.exception.notification.NotificationMappingException;
import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.mapper.NotificationMapper;
import codeit.sb06.otboo.notification.repository.NotificationRepository;
import codeit.sb06.otboo.notification.service.NotificationCacheService;
import codeit.sb06.otboo.notification.util.SseEventIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCacheServiceImpl implements NotificationCacheService {

    public static final int TIMEOUT = 5;
    private static final int MAX_NOTIFICATIONS = 50;
    private static final String KEY_PREFIX = "notifications:user:";
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final SseEventIdGenerator sseEventIdGenerator;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(NotificationDto dto) {

        String key = KEY_PREFIX + dto.receiverId();
        String json = toJson(dto);

        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(@NonNull RedisOperations operations) {
                addCacheCommands(operations, key, json);
                return null;
            }
        });
    }

    @Override
    public void saveAll(List<NotificationDto> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) return;

        // 파이프라인을 열어 명령어들을 모아 한 번에 전송
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(@NonNull RedisOperations operations) {

                for (NotificationDto dto : dtoList) {

                    String key = KEY_PREFIX + dto.receiverId();
                    String json = toJson(dto);

                    addCacheCommands(operations, key, json);
                }
                return null;
            }
        });
    }

    @Override
    public List<NotificationDto> getRecentNotifications(UUID userId) {

        String key = KEY_PREFIX + userId;

        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);

        // 캐시에 데이터가 있으면 캐시에서 반환
        if (jsonList != null && !jsonList.isEmpty()) {
            return jsonList.stream()
                    .map(this::parseJson)
                    .toList();
        }

        // 캐시에 데이터가 없으면 DB에서 조회 후 캐시에 저장, 반환
        PageRequest pageRequest = PageRequest.of(0, MAX_NOTIFICATIONS);
        List<NotificationDto> dtoList = notificationRepository.findFirstPageByReceiverId(userId, pageRequest)
                .getContent()
                .stream()
                .map(notificationMapper::toDto)
                .toList();

        if(!dtoList.isEmpty()) {
            List<String> serializedList = dtoList.stream()
                    .map(this::toJson)
                    .toList();
            redisTemplate.opsForList().rightPushAll(key, serializedList);
            redisTemplate.expire(key, TIMEOUT, TimeUnit.DAYS);
        }

        return dtoList;
    }

    @Override
    public List<NotificationDto> getNotificationsAfter(UUID userId, String lastEventId) {
        List<NotificationDto> recentNotifications = getRecentNotifications(userId);

        if (!StringUtils.hasText(lastEventId)) {
            return recentNotifications;
        }

        return recentNotifications.stream()
                .filter(dto -> {
                    String eventId = sseEventIdGenerator.generator(dto.createdAt(), userId);
                    return eventId.compareTo(lastEventId) > 0;
                })
                .toList();
    }

    private String toJson(NotificationDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new NotificationMappingException();
        }
    }

    private NotificationDto parseJson(String json) {
        try {
            return objectMapper.readValue(json, NotificationDto.class);
        } catch (Exception e) {
            throw new NotificationMappingException();
        }
    }

    @SuppressWarnings("unchecked")
    private void addCacheCommands(RedisOperations operations, String key, String json) {
        operations.opsForList().leftPush(key, json);
        operations.opsForList().trim(key, 0, MAX_NOTIFICATIONS - 1L);
        operations.expire(key, TIMEOUT, TimeUnit.DAYS);
    }
}
