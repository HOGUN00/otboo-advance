package codeit.sb06.otboo.message.service;

import codeit.sb06.otboo.message.entity.ChatMember;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.service.impl.ChatRoomIdResolver;
import codeit.sb06.otboo.user.entity.User;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRoomIdResolverTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatMemberService chatMemberService;
    @InjectMocks
    private ChatRoomIdResolver chatRoomIdResolver;

    @Test
    @DisplayName("기존 1대1 채팅방의 ID를 반환한다.")
    void resolveExistingChatRoomId() {
        // given
        UUID chatRoomId = UUID.randomUUID();
        String dmKey = "sender_receiver";
        User sender = mock(User.class);
        User receiver = mock(User.class);
        ChatRoom chatRoom = new ChatRoom(chatRoomId, dmKey, new HashSet<>());
        given(chatRoomRepository.findByDmKey(dmKey)).willReturn(Optional.of(chatRoom));

        // when
        UUID result = chatRoomIdResolver.resolve(dmKey, sender, receiver);

        // then
        assertThat(result).isEqualTo(chatRoomId);
        verify(chatRoomRepository, never()).insertIfAbsent(any(UUID.class), eq(dmKey));
        verify(chatMemberService, never()).create(any(ChatRoom.class), any(User.class));
    }

    @Test
    @DisplayName("1대1 채팅방을 생성하고 ID를 반환한다.")
    void resolveNewChatRoomId() {
        // given
        UUID chatRoomId = UUID.randomUUID();
        String dmKey = "sender_receiver";
        User sender = mock(User.class);
        User receiver = mock(User.class);
        ChatRoom chatRoom = new ChatRoom(chatRoomId, dmKey, new HashSet<>());
        given(chatRoomRepository.findByDmKey(dmKey))
                .willReturn(Optional.empty(), Optional.of(chatRoom));
        given(chatRoomRepository.insertIfAbsent(any(UUID.class), eq(dmKey))).willReturn(1);
        given(chatMemberService.create(chatRoom, sender)).willReturn(mock(ChatMember.class));
        given(chatMemberService.create(chatRoom, receiver)).willReturn(mock(ChatMember.class));

        // when
        UUID result = chatRoomIdResolver.resolve(dmKey, sender, receiver);

        // then
        assertThat(result).isEqualTo(chatRoomId);
        verify(chatRoomRepository).insertIfAbsent(any(UUID.class), eq(dmKey));
        verify(chatMemberService, times(1)).create(chatRoom, sender);
        verify(chatMemberService, times(1)).create(chatRoom, receiver);
        assertThat(chatRoom.getChatMembers()).hasSize(2);
    }

    @Test
    @DisplayName("동시에 생성된 1대1 채팅방을 재조회해 ID를 반환한다.")
    void resolveChatRoomIdCreatedByConcurrentRequest() {
        // given
        UUID chatRoomId = UUID.randomUUID();
        String dmKey = "sender_receiver";
        User sender = mock(User.class);
        User receiver = mock(User.class);
        ChatRoom chatRoom = new ChatRoom(chatRoomId, dmKey, new HashSet<>());
        given(chatRoomRepository.findByDmKey(dmKey))
                .willReturn(Optional.empty(), Optional.of(chatRoom));
        given(chatRoomRepository.insertIfAbsent(any(UUID.class), eq(dmKey))).willReturn(0);

        // when
        UUID result = chatRoomIdResolver.resolve(dmKey, sender, receiver);

        // then
        assertThat(result).isEqualTo(chatRoomId);
        verify(chatRoomRepository).insertIfAbsent(any(UUID.class), eq(dmKey));
        verify(chatMemberService, never()).create(any(ChatRoom.class), any(User.class));
    }
}
