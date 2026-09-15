package codeit.sb06.otboo.message.service;

import codeit.sb06.otboo.message.config.ChatRoomCacheConfig;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.service.impl.ChatRoomIdResolver;
import codeit.sb06.otboo.user.entity.User;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ChatRoomIdResolverCacheTest {

    private AnnotationConfigApplicationContext context;
    private ChatRoomRepository chatRoomRepository;
    private ChatRoomIdResolver chatRoomIdResolver;

    @BeforeEach
    void setUp() {
        chatRoomRepository = mock(ChatRoomRepository.class);
        ChatMemberService chatMemberService = mock(ChatMemberService.class);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(ChatRoomRepository.class, () -> chatRoomRepository);
        context.registerBean(ChatMemberService.class, () -> chatMemberService);
        context.register(ChatRoomCacheConfig.class, ChatRoomIdResolver.class);
        TestPropertyValues.of(
                "otboo.cache.chat-room.maximum-size=100000",
                "otboo.cache.chat-room.expire-after-access=1h"
        ).applyTo(context);
        context.refresh();
        chatRoomIdResolver = context.getBean(ChatRoomIdResolver.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("같은 dmKey를 다시 조회하면 Cache에서 ChatRoom ID를 반환한다.")
    void cacheResolvedChatRoomId() {
        // given
        String dmKey = "sender_receiver";
        UUID chatRoomId = UUID.randomUUID();
        User sender = mock(User.class);
        User receiver = mock(User.class);
        ChatRoom chatRoom = new ChatRoom(chatRoomId, dmKey, new HashSet<>());
        given(chatRoomRepository.findByDmKey(dmKey)).willReturn(Optional.of(chatRoom));

        // when
        UUID first = chatRoomIdResolver.resolve(dmKey, sender, receiver);
        UUID second = chatRoomIdResolver.resolve(dmKey, sender, receiver);

        // then
        assertThat(first).isEqualTo(chatRoomId);
        assertThat(second).isEqualTo(chatRoomId);
        verify(chatRoomRepository, times(1)).findByDmKey(dmKey);
    }
}
