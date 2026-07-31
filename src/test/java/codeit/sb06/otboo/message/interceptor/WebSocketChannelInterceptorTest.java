package codeit.sb06.otboo.message.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import codeit.sb06.otboo.exception.auth.InvalidTokenException;
import codeit.sb06.otboo.security.jwt.JwtRegistry;
import codeit.sb06.otboo.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WebSocketChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtRegistry jwtRegistry;

    @Mock
    private MessageChannel channel;

    @InjectMocks
    private WebSocketChannelInterceptor webSocketChannelInterceptor;

    @Test
    @DisplayName("웹소켓 메시지 전송 전 인터셉트 테스트")
    void preSendTest() {
        // given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer dummyAccessToken");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        UUID userId = UUID.randomUUID();
        given(jwtTokenProvider.validateAccessToken(anyString())).willReturn(true);
        given(jwtRegistry.hasActiveJwtInformationByAccessToken(anyString())).willReturn(true);
        given(jwtTokenProvider.getUserId(anyString())).willReturn(userId);

        // when
        Message<?> result = webSocketChannelInterceptor.preSend(message, channel);

        // then
        StompHeaderAccessor resultAccessor =
                MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(result).isNotNull();
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("웹소켓 메시지 전송 전 인터셉트 - 유효하지 않은 토큰 테스트")
    void preSendInvalidTokenTest() {
        // given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer invalidAccessToken");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        given(jwtTokenProvider.validateAccessToken(anyString())).willReturn(false);

        // when & then
        assertThatThrownBy(() ->
                webSocketChannelInterceptor.preSend(message, channel))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("메시지 예외 발생시 로그 테스트")
    void afterSendCompletionTest() {
        // given
        Logger logger = (Logger) LoggerFactory.getLogger(WebSocketChannelInterceptor.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        Message<String> message = MessageBuilder.withPayload("test").build();
        Exception exception = new RuntimeException("연결 강제 종료");

        // when
        webSocketChannelInterceptor.afterSendCompletion(message, channel, false, exception);

        // then
        assertThat(listAppender.list).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
    }
}
