package codeit.sb06.otboo.message.service;

import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.service.impl.ChatRoomIdResolver;
import codeit.sb06.otboo.message.service.impl.ChatRoomServiceImpl;
import codeit.sb06.otboo.user.entity.User;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceImplTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomIdResolver chatRoomIdResolver;
    @InjectMocks
    private ChatRoomServiceImpl chatRoomService;

    @Test
    @DisplayName("해결된 ChatRoom ID로 DB 조회 없이 채팅방 참조를 반환한다.")
    void getPrivateChatRoomByResolvedId() {
        // given
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID chatRoomId = UUID.randomUUID();
        User sender = mock(User.class);
        User receiver = mock(User.class);
        given(sender.getId()).willReturn(senderId);
        given(receiver.getId()).willReturn(receiverId);
        String dmKey = ChatRoom.generateDmKey(senderId, receiverId);
        ChatRoom chatRoom = new ChatRoom(chatRoomId, dmKey, new HashSet<>());
        given(chatRoomIdResolver.resolve(dmKey, sender, receiver)).willReturn(chatRoomId);
        given(chatRoomRepository.getReferenceById(chatRoomId)).willReturn(chatRoom);

        // when
        ChatRoom result = chatRoomService.getOrCreatePrivateRoom(sender, receiver);

        // then
        assertThat(result).isSameAs(chatRoom);
        verify(chatRoomIdResolver).resolve(dmKey, sender, receiver);
        verify(chatRoomRepository).getReferenceById(chatRoomId);
    }
}
