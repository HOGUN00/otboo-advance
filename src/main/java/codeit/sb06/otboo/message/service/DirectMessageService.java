package codeit.sb06.otboo.message.service;

import codeit.sb06.otboo.message.dto.DirectMessageDto;
import codeit.sb06.otboo.message.dto.request.DirectMessageCreateRequest;
import codeit.sb06.otboo.message.dto.response.DirectMessageDtoCursorResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DirectMessageService {

    DirectMessageDto create(UUID authenticatedSenderId, DirectMessageCreateRequest request);

    DirectMessageDtoCursorResponse getDirectMessages(UUID myUserId, UUID senderId, LocalDateTime cursor, UUID idAfter, int limit);
}
