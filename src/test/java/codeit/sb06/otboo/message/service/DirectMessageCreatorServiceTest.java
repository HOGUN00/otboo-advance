package codeit.sb06.otboo.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.message.dto.DirectMessageCreation;
import codeit.sb06.otboo.message.dto.request.DirectMessageCreateRequest;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.entity.DirectMessage;
import codeit.sb06.otboo.message.mapper.DirectMessageMapper;
import codeit.sb06.otboo.message.repository.DirectMessageRepository;
import codeit.sb06.otboo.message.service.impl.DirectMessageCreatorService;
import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.service.NotificationService;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import codeit.sb06.otboo.util.EasyRandomUtil;
import java.util.List;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectMessageCreatorServiceTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();

    @Mock
    private DirectMessageRepository directMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private NotificationService notificationService;

    @Spy
    private DirectMessageMapper directMessageMapper;

    @InjectMocks
    private DirectMessageCreatorService creatorService;

    @Test
    @DisplayName("DM과 알림을 저장하고 Redis 발행에 필요한 결과를 반환한다")
    void createReturnsRedisPublicationResult() {
        DirectMessageCreateRequest request = easyRandom.nextObject(DirectMessageCreateRequest.class);
        User sender = mock(User.class);
        User receiver = mock(User.class);
        ChatRoom chatRoom = mock(ChatRoom.class);
        NotificationDto notificationDto = easyRandom.nextObject(NotificationDto.class);
        given(sender.getId()).willReturn(request.senderId());
        given(sender.getName()).willReturn("sender");
        given(receiver.getId()).willReturn(request.receiverId());
        given(userRepository.findAllById(any())).willReturn(List.of(sender, receiver));
        given(chatRoomService.getOrCreatePrivateRoom(sender, receiver)).willReturn(chatRoom);
        given(directMessageRepository.save(any(DirectMessage.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(notificationService.createDirectMessageInCurrentTransaction(
                request.receiverId(),
                "sender",
                request.content()))
                .willReturn(notificationDto);

        DirectMessageCreation creation = creatorService.create(request.senderId(), request);

        assertThat(creation.directMessageDto().content()).isEqualTo(request.content());
        assertThat(creation.notificationDto()).isEqualTo(notificationDto);
        assertThat(creation.destination()).isEqualTo(
                "/sub/direct-messages_" + ChatRoom.generateDmKey(request.senderId(), request.receiverId()));
    }

    @Test
    @DisplayName("수신자가 존재하지 않으면 DM과 알림을 저장하지 않는다")
    void createWithMissingReceiverFails() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        DirectMessageCreateRequest request = new DirectMessageCreateRequest(
                receiverId,
                senderId,
                "content");
        User sender = mock(User.class);
        given(sender.getId()).willReturn(senderId);
        given(userRepository.findAllById(any())).willReturn(List.of(sender));

        assertThatThrownBy(() -> creatorService.create(senderId, request))
                .isInstanceOf(UserNotFoundException.class);
        verify(chatRoomService, never()).getOrCreatePrivateRoom(any(), any());
        verify(directMessageRepository, never()).save(any());
        verify(notificationService, never()).createDirectMessageInCurrentTransaction(
                any(), any(), any());
    }
}
