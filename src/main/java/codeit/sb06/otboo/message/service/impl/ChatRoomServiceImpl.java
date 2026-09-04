package codeit.sb06.otboo.message.service.impl;

import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.service.ChatMemberService;
import codeit.sb06.otboo.message.service.ChatRoomService;
import codeit.sb06.otboo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberService chatMemberService;

    public ChatRoom getOrCreatePrivateRoom(User sender, User receiver) {

        String dmKey = ChatRoom.generateDmKey(sender.getId(), receiver.getId());

        return chatRoomRepository.findByDmKey(dmKey)
                .orElseGet(() -> {
                    ChatRoom newRoom = chatRoomRepository.save(new ChatRoom(dmKey));
                    newRoom.addChatMember(chatMemberService.create(newRoom, sender));
                    newRoom.addChatMember(chatMemberService.create(newRoom, receiver));
                    return newRoom;
                });
    }
}
