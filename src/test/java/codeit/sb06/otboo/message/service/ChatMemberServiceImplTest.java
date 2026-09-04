package codeit.sb06.otboo.message.service;


import codeit.sb06.otboo.message.entity.ChatMember;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.repository.ChatMemberRepository;
import codeit.sb06.otboo.message.service.impl.ChatMemberServiceImpl;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.util.EasyRandomUtil;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMemberServiceImplTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();
    @Mock
    private ChatMemberRepository chatMemberRepository;
    @InjectMocks
    private ChatMemberServiceImpl chatMemberService;

    @Test
    @DisplayName("채팅 멤버를 생성하고 반환한다.")
    void createChatMemberTest() {
        //given
        ChatRoom chatRoom = mock(ChatRoom.class);
        User user = easyRandom.nextObject(User.class);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        given(chatMemberRepository.save(any(ChatMember.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        ChatMember createdChatMember = chatMemberService.create(chatRoom, user);

        // then
        assertAll(
                () -> assertThat(createdChatMember.getUser()).isEqualTo(user),
                () -> assertThat(createdChatMember.getChatRoom()).isEqualTo(chatRoom),
                () -> verify(chatMemberRepository, times(1)).save(any())
        );
    }

}
