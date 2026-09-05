package codeit.sb06.otboo.message.service.impl;

import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.service.ChatMemberService;
import codeit.sb06.otboo.message.service.ChatRoomService;
import codeit.sb06.otboo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberService chatMemberService;

    public ChatRoom getOrCreatePrivateRoom(User sender, User receiver) {

        String dmKey = ChatRoom.generateDmKey(sender.getId(), receiver.getId());

        return chatRoomRepository.findByDmKey(dmKey)
                .orElseGet(() -> createPrivateRoomIfAbsent(dmKey, sender, receiver));
    }

    private ChatRoom createPrivateRoomIfAbsent(String dmKey, User sender, User receiver) {
        int inserted = chatRoomRepository.insertIfAbsent(UUID.randomUUID(), dmKey);
        ChatRoom chatRoom = chatRoomRepository.findByDmKey(dmKey)
                .orElseThrow(() -> new IllegalStateException("채팅방 생성 또는 조회에 실패했습니다."));

        if (inserted == 1) {
            chatRoom.addChatMember(chatMemberService.create(chatRoom, sender));
            chatRoom.addChatMember(chatMemberService.create(chatRoom, receiver));
        }

        return chatRoom;
    }
}
