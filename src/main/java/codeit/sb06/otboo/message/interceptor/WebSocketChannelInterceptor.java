package codeit.sb06.otboo.message.interceptor;

import codeit.sb06.otboo.exception.auth.InvalidTokenException;
import codeit.sb06.otboo.security.jwt.JwtRegistry;
import codeit.sb06.otboo.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtRegistry jwtRegistry;

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            String bearerToken = accessor.getFirstNativeHeader("Authorization");

            if (!StringUtils.hasText(bearerToken)
                    || !bearerToken.startsWith("Bearer ")) {
                throw new InvalidTokenException();
            }

            String jwtToken = bearerToken.substring(7);

            if (!jwtTokenProvider.validateAccessToken(jwtToken)
                    || !jwtRegistry.hasActiveJwtInformationByAccessToken(jwtToken)) {
                throw new InvalidTokenException();
            }

            UUID userId = jwtTokenProvider.getUserId(jwtToken);
            if (userId == null) {
                throw new InvalidTokenException();
            }

            accessor.setUser(() -> userId.toString());
        }

        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, @Nullable Exception ex) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // HEARTBEAT 메시지는 로그 제외
        if (accessor != null && SimpMessageType.HEARTBEAT.equals(accessor.getMessageType())) {
            return;
        }

        if (ex != null) {
            log.error("웹소켓 메시지 전송 중 에러 발생: {}", ex.getMessage());
        } else if (!sent) {
            log.warn("웹소켓 메시지가 전송되지 않았습니다: {}", message);
        } else {
            log.debug("웹소켓 메시지 전송이 완료되었습니다: {}", message);
        }
    }
}
