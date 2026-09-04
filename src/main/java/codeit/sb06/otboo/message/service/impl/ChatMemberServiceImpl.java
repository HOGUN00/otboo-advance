package codeit.sb06.otboo.message.service.impl;

import codeit.sb06.otboo.message.entity.ChatMember;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.repository.ChatMemberRepository;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.message.service.ChatMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatMemberServiceImpl implements ChatMemberService {

    private final ChatMemberRepository chatMemberRepository;

    @Override
    public ChatMember create(ChatRoom chatRoom, User user) {

        ChatMember chatMember = ChatMember.builder()
                .chatRoom(chatRoom)
                .user(user)
                .build();
        return chatMemberRepository.save(chatMember);
    }
}
