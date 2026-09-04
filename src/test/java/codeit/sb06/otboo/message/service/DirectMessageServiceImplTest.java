package codeit.sb06.otboo.message.service;

import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.message.dto.DirectMessageDto;
import codeit.sb06.otboo.message.dto.request.DirectMessageCreateRequest;
import codeit.sb06.otboo.message.dto.response.DirectMessageDtoCursorResponse;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.entity.DirectMessage;
import codeit.sb06.otboo.message.mapper.DirectMessageMapper;
import codeit.sb06.otboo.message.publisher.DirectMessageEventPublisher;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.repository.DirectMessageRepository;
import codeit.sb06.otboo.message.service.impl.DirectMessageServiceImpl;
import codeit.sb06.otboo.notification.publisher.NotificationEventPublisher;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import codeit.sb06.otboo.util.EasyRandomUtil;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceImplTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();

    @Mock
    private DirectMessageRepository directMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private DirectMessageEventPublisher dmEventPublisher;

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

        User sender = mock(User.class);
        User receiver = mock(User.class);
        given(sender.getId()).willReturn(request.senderId());
        given(receiver.getId()).willReturn(request.receiverId());
        given(userRepository.findAllById(any()))
                .willReturn(List.of(sender, receiver));
        given(chatRoomService.getOrCreatePrivateRoom(sender, receiver))
                .willReturn(mock(ChatRoom.class));
        given(directMessageRepository.save(any(DirectMessage.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        UUID authenticatedSenderId = request.senderId();
        DirectMessageDto dmDto = directMessageService.create(authenticatedSenderId, request);

        // then
        assertAll(
                () -> assertThat(dmDto).isNotNull(),
                () -> assertThat(dmDto.content()).isEqualTo(request.content())
        );
    }

    @Test
    @DisplayName("수신자가 존재하지 않으면 DM 생성에 실패한다")
    void createDirectMessageWithMissingReceiverFails() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        DirectMessageCreateRequest request = new DirectMessageCreateRequest(
                receiverId,
                senderId,
                "content");
        User sender = mock(User.class);

        given(sender.getId()).willReturn(senderId);
        given(userRepository.findAllById(any())).willReturn(List.of(sender));

        assertThatThrownBy(() -> directMessageService.create(senderId, request))
                .isInstanceOf(UserNotFoundException.class);
        verify(chatRoomService, never()).getOrCreatePrivateRoom(any(), any());
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
