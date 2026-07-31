package codeit.sb06.otboo.message.controller;

import codeit.sb06.otboo.message.dto.request.DirectMessageCreateRequest;
import codeit.sb06.otboo.message.service.DirectMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

    private final DirectMessageService directMessageService;

    @MessageMapping("/direct-messages_send")
    public void sendDirectMessage(
            Principal principal,
            @Payload @Valid DirectMessageCreateRequest request
    ) {
        log.debug("웹소켓 직접 메시지 전송 요청 받음: {}", request);

        UUID authenticatedSenderId = UUID.fromString(principal.getName());
        directMessageService.create(authenticatedSenderId, request);
    }
}
