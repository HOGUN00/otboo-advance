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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private static final String BROKER_PREFIX = "/sub";
    private static final String DM_DESTINATION_PREFIX = "/sub/direct-messages_";

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
        } else if (StompCommand.SEND.equals(command)) {
            authorizeSend(accessor.getDestination());
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscribe(accessor.getUser(), accessor.getDestination());
        }

        return message;
    }

    private void authorizeSend(String destination) {
        if (isBrokerDestination(destination)) {
            throw new AccessDeniedException("브로커 destination으로 직접 전송할 수 없습니다.");
        }
    }

    private void authorizeSubscribe(Principal principal, String destination) {
        if (!isBrokerDestination(destination)) {
            return;
        }

        if (principal == null || !StringUtils.hasText(destination)
                || !StringUtils.hasText(principal.getName())
                || !destination.startsWith(DM_DESTINATION_PREFIX)) {
            throw new AccessDeniedException("구독할 수 없는 destination입니다.");
        }

        String[] participantIds = destination.substring(DM_DESTINATION_PREFIX.length()).split("_", -1);
        if (participantIds.length != 2) {
            throw new AccessDeniedException("유효하지 않은 DM destination입니다.");
        }

        try {
            UUID authenticatedUserId = UUID.fromString(principal.getName());
            UUID firstParticipantId = UUID.fromString(participantIds[0]);
            UUID secondParticipantId = UUID.fromString(participantIds[1]);

            if (!authenticatedUserId.equals(firstParticipantId)
                    && !authenticatedUserId.equals(secondParticipantId)) {
                throw new AccessDeniedException("참여하지 않은 DM은 구독할 수 없습니다.");
            }
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("유효하지 않은 DM destination입니다.", e);
        }
    }

    private boolean isBrokerDestination(String destination) {
        return destination != null && destination.startsWith(BROKER_PREFIX);
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
