package codeit.sb06.otboo.notification.service;

import codeit.sb06.otboo.exception.notification.NotificationMappingException;
import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.entity.Notification;
import codeit.sb06.otboo.notification.mapper.NotificationMapper;
import codeit.sb06.otboo.notification.repository.NotificationRepository;
import codeit.sb06.otboo.notification.service.impl.NotificationCacheServiceImpl;
import codeit.sb06.otboo.notification.util.SseEventIdGenerator;
import codeit.sb06.otboo.util.EasyRandomUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationCacheServiceUnitTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();

    @Mock
    private NotificationRepository notificationRepository;

    @Spy
    private NotificationMapper notificationMapper;

    @Spy
    private SseEventIdGenerator sseEventIdGenerator;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    @Mock
    private RedisOperations<String, String> redisOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationCacheServiceImpl notificationCacheService;

    @Test
    @DisplayName("dto 직렬화 실패 시 JsonProcessingException이 발생한다.")
    void cacheNotificationTest() throws JsonProcessingException {
        // given
        NotificationDto dto = easyRandom.nextObject(NotificationDto.class);

        given(objectMapper.writeValueAsString(dto))
                .willThrow(new JsonProcessingException("Force Serialization Error") {
                });

        // when & then
        assertThatThrownBy(() -> notificationCacheService.save(dto))
                .isInstanceOf(NotificationMappingException.class);
    }

    @Test
    @DisplayName("알림 한 건을 저장할 때 Cache 명령을 Pipeline으로 실행한다.")
    @SuppressWarnings("unchecked")
    void saveNotificationWithPipelineTest() throws JsonProcessingException {
        // given
        NotificationDto dto = easyRandom.nextObject(NotificationDto.class);
        String key = "notifications:user:" + dto.receiverId();
        String json = "notification-json";
        given(objectMapper.writeValueAsString(dto)).willReturn(json);
        given(redisOperations.opsForList()).willReturn(listOps);

        // when
        notificationCacheService.save(dto);

        // then
        ArgumentCaptor<SessionCallback<Object>> callbackCaptor =
                ArgumentCaptor.forClass(SessionCallback.class);
        verify(redisTemplate).executePipelined(callbackCaptor.capture());
        callbackCaptor.getValue().execute(redisOperations);
        verify(listOps).leftPush(key, json);
        verify(listOps).trim(key, 0, 49);
        verify(redisOperations).expire(key, 5, TimeUnit.DAYS);
    }

    @Test
    @DisplayName("알림 캐시에 데이터가 없으면 db에서 조회하고 캐시에 저장한다.")
    void getNotificationsAfterTest() {
        // given
        UUID userId = UUID.randomUUID();
        String key = "notifications:user:" + userId;
        given(redisTemplate.opsForList()).willReturn(listOps);
        given(listOps.range(key, 0, -1))
                .willReturn(List.of());

        List<Notification> dtoList = easyRandom.objects(Notification.class, 3).toList();
        PageRequest pageRequest = PageRequest.of(0, 10);
        Slice<Notification> slice = new SliceImpl<>(dtoList, pageRequest, false);
        given(notificationRepository.findFirstPageByReceiverId(any(), any()))
                .willReturn(slice);

        // when
        List<NotificationDto> response = notificationCacheService.getRecentNotifications(userId);

        // then
        assertThat(response).hasSize(3);
    }

    @Test
    @DisplayName("json으로부터 dto 변환 실패 시 NotificationMappingException이 발생한다.")
    void getNotificationsAfterMappingExceptionTest() throws JsonProcessingException {
        // given
        UUID userId = UUID.randomUUID();
        String key = "notifications:user:" + userId;
        String invalidJson = "invalid json string";

        given(redisTemplate.opsForList()).willReturn(listOps);
        given(listOps.range(key, 0, -1))
                .willReturn(List.of(invalidJson));

        given(objectMapper.readValue(invalidJson, NotificationDto.class))
                .willThrow(new JsonProcessingException("Force Deserialization Error") {
                });

        // when & then
        assertThatThrownBy(() -> notificationCacheService.getRecentNotifications(userId))
                .isInstanceOf(NotificationMappingException.class);
    }
}
