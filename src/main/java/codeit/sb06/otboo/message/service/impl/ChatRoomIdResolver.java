package codeit.sb06.otboo.message.service.impl;

import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.service.ChatMemberService;
import codeit.sb06.otboo.user.entity.User;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatRoomIdResolver {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberService chatMemberService;

    @Cacheable(cacheNames = "chatRoomIds", key = "#dmKey")
    public UUID resolve(String dmKey, User sender, User receiver) {
        return chatRoomRepository.findByDmKey(dmKey)
                .map(ChatRoom::getId)
                .orElseGet(() -> createPrivateRoomIfAbsent(dmKey, sender, receiver));
    }

    private UUID createPrivateRoomIfAbsent(String dmKey, User sender, User receiver) {
        int inserted = chatRoomRepository.insertIfAbsent(UUID.randomUUID(), dmKey);
        ChatRoom chatRoom = chatRoomRepository.findByDmKey(dmKey)
                .orElseThrow(() -> new IllegalStateException("채팅방 생성 또는 조회에 실패했습니다."));

        if (inserted == 1) {
            chatRoom.addChatMember(chatMemberService.create(chatRoom, sender));
            chatRoom.addChatMember(chatMemberService.create(chatRoom, receiver));
        }

        return chatRoom.getId();
    }
}
