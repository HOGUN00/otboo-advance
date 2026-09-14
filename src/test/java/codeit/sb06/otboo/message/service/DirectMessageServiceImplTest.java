package codeit.sb06.otboo.message.service;

import codeit.sb06.otboo.message.dto.DirectMessageCreation;
import codeit.sb06.otboo.message.dto.DirectMessageDto;
import codeit.sb06.otboo.message.dto.request.DirectMessageCreateRequest;
import codeit.sb06.otboo.message.dto.response.DirectMessageDtoCursorResponse;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.entity.DirectMessage;
import codeit.sb06.otboo.message.mapper.DirectMessageMapper;
import codeit.sb06.otboo.message.publisher.DirectMessageRedisPublisher;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.repository.DirectMessageRepository;
import codeit.sb06.otboo.message.service.impl.DirectMessageCreatorService;
import codeit.sb06.otboo.message.service.impl.DirectMessageServiceImpl;
import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.publisher.RedisNotificationPublisher;
import codeit.sb06.otboo.notification.service.NotificationCacheService;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import codeit.sb06.otboo.util.EasyRandomUtil;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceImplTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();

    @Mock
    private DirectMessageRepository directMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DirectMessageCreatorService creatorService;

    @Mock
    private NotificationCacheService notificationCacheService;

    @Mock
    private RedisNotificationPublisher redisNotificationPublisher;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private DirectMessageRedisPublisher directMessageRedisPublisher;

    // 실제 변환을 위해 spy 사용
    @Spy
    private DirectMessageMapper directMessageMapper;

    @InjectMocks
    private DirectMessageServiceImpl directMessageService;

    @Test
    @DisplayName("Direct Message 생성 테스트를 성공한다.")
    void createDirectMessageTest() {
        // given
        DirectMessageCreateRequest request = easyRandom.nextObject(DirectMessageCreateRequest.class);
        DirectMessageDto dto = easyRandom.nextObject(DirectMessageDto.class);
        NotificationDto notificationDto = easyRandom.nextObject(NotificationDto.class);
        String destination = "/sub/direct-messages_key";
        DirectMessageCreation creation = new DirectMessageCreation(
                dto,
                notificationDto,
                destination);
        UUID authenticatedSenderId = request.senderId();
        given(creatorService.create(authenticatedSenderId, request)).willReturn(creation);

        // when
        DirectMessageDto actual = directMessageService.create(authenticatedSenderId, request);

        // then
        assertThat(actual).isEqualTo(dto);
        InOrder inOrder = inOrder(
                creatorService,
                directMessageRedisPublisher,
                notificationCacheService,
                redisNotificationPublisher);
        inOrder.verify(creatorService).create(authenticatedSenderId, request);
        inOrder.verify(directMessageRedisPublisher).publish(dto, destination);
        inOrder.verify(notificationCacheService).save(notificationDto);
        inOrder.verify(redisNotificationPublisher).publish(notificationDto);
    }

    @Test
    @DisplayName("Direct Message 커서 페이지네이션 조회 테스트를 성공한다.")
    void getDirectMessagesWithCursorTest() {
        // given
        UUID myUserId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        int limit = 10;
        ChatRoom mockChatRoom = mock(ChatRoom.class);
        List<DirectMessage> directMessages = easyRandom.objects(DirectMessage.class, limit + limit).toList();
        List<DirectMessage> top10 = directMessages.subList(0, limit);
        Slice<DirectMessage> directMessageSlice = new SliceImpl<>(top10, PageRequest.of(0, 10), true);

        given(chatRoomRepository.findByDmKey(anyString()))
                .willReturn(Optional.of(mockChatRoom));
        given(userRepository.findById(myUserId))
                .willReturn(Optional.of(mock(User.class)));
        given(directMessageRepository.findFirstPageByChatRoom(
                mockChatRoom, PageRequest.of(0, limit)))
                .willReturn(directMessageSlice);

        // when
        DirectMessageDtoCursorResponse response = directMessageService.getDirectMessages(
                myUserId,
                senderId,
                null,
                null,
                limit);

        // then
        assertAll(
                () -> assertThat(response).isNotNull(),
                () -> assertThat(response.data()).hasSize(limit)
        );
    }
}
