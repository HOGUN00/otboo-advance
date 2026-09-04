package codeit.sb06.otboo.message.service;

import codeit.sb06.otboo.message.entity.ChatMember;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.service.impl.ChatRoomServiceImpl;
import codeit.sb06.otboo.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceImplTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatMemberService chatMemberService;
    @InjectMocks
    private ChatRoomServiceImpl chatRoomService;

    @Test
    @DisplayName("1대1 채팅방을 생성하고 반환한다.")
    void testCreatePrivateChatRoom() {
        // given
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        User sender = mock(User.class);
        User receiver = mock(User.class);
        given(sender.getId()).willReturn(senderId);
        given(receiver.getId()).willReturn(receiverId);
        String dmKey = ChatRoom.generateDmKey(senderId, receiverId);

        given(chatRoomRepository.findByDmKey(dmKey))
                .willReturn(Optional.empty());
        given(chatRoomRepository.save(any(ChatRoom.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(chatMemberService.create(any(ChatRoom.class), eq(sender)))
                .willReturn(mock(ChatMember.class));
        given(chatMemberService.create(any(ChatRoom.class), eq(receiver)))
                .willReturn(mock(ChatMember.class));

        // when
        ChatRoom createdChatRoom = chatRoomService.getOrCreatePrivateRoom(sender, receiver);

        // then
        assertAll(
                () -> assertThat(createdChatRoom.getDmKey()).isEqualTo(dmKey),
                () -> verify(chatMemberService, times(1)).create(any(ChatRoom.class), eq(sender)),
                () -> verify(chatMemberService, times(1)).create(any(ChatRoom.class), eq(receiver)),
                () -> verify(chatRoomRepository, times(1)).save(any(ChatRoom.class)),
                () -> assertThat(createdChatRoom.getChatMembers()).hasSize(2)
        );
    }

    @Test
    @DisplayName("기존 1대1 채팅방을 반환한다.")
    void testGetExistingPrivateChatRoom() {
        // given
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        User sender = mock(User.class);
        User receiver = mock(User.class);
        given(sender.getId()).willReturn(senderId);
        given(receiver.getId()).willReturn(receiverId);
        String dmKey = ChatRoom.generateDmKey(senderId, receiverId);
        ChatRoom existingChatRoom = new ChatRoom(dmKey);

        given(chatRoomRepository.findByDmKey(dmKey))
                .willReturn(Optional.of(existingChatRoom));

        // when
        ChatRoom chatRoom = chatRoomService.getOrCreatePrivateRoom(sender, receiver);

        // then
        assertAll(
                () -> assertThat(chatRoom).usingRecursiveComparison().isEqualTo(existingChatRoom), // 필드 비교
                () -> verify(chatRoomRepository, never()).save(any(ChatRoom.class)),
                () -> verify(chatMemberService, never()).create(any(ChatRoom.class), any(User.class))
        );
    }
}
