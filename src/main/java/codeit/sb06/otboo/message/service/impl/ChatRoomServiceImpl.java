package codeit.sb06.otboo.message.service.impl;

import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.service.ChatRoomService;
import codeit.sb06.otboo.user.entity.User;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomIdResolver chatRoomIdResolver;

    public ChatRoom getOrCreatePrivateRoom(User sender, User receiver) {
        String dmKey = ChatRoom.generateDmKey(sender.getId(), receiver.getId());
        UUID chatRoomId = chatRoomIdResolver.resolve(dmKey, sender, receiver);
        // DirectMessage 연관관계에 사용할 proxy만 생성해 ChatRoom SELECT를 생략한다.
        return chatRoomRepository.getReferenceById(chatRoomId);
    }
}
