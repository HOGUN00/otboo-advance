package codeit.sb06.otboo.message.service.impl;

import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.message.dto.DirectMessageCreation;
import codeit.sb06.otboo.message.dto.DirectMessageDto;
import codeit.sb06.otboo.message.dto.request.DirectMessageCreateRequest;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.entity.DirectMessage;
import codeit.sb06.otboo.message.mapper.DirectMessageMapper;
import codeit.sb06.otboo.message.repository.DirectMessageRepository;
import codeit.sb06.otboo.message.service.ChatRoomService;
import codeit.sb06.otboo.notification.dto.NotificationDto;
import codeit.sb06.otboo.notification.service.NotificationService;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectMessageCreatorService {

    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;
    private final ChatRoomService chatRoomService;
    private final DirectMessageMapper directMessageMapper;
    private final NotificationService notificationService;

    @Transactional
    public DirectMessageCreation create(UUID authenticatedSenderId, DirectMessageCreateRequest request) {

        if (!authenticatedSenderId.equals(request.senderId())) {
            throw new AccessDeniedException("발신자 정보가 일치하지 않습니다.");
        }

        List<User> participants = userRepository.findAllById(
                List.of(authenticatedSenderId, request.receiverId()));

        User sender = participants.stream()
                .filter(user -> user.getId().equals(authenticatedSenderId))
                .findFirst()
                .orElseThrow(UserNotFoundException::new);
        User receiver = participants.stream()
                .filter(user -> user.getId().equals(request.receiverId()))
                .findFirst()
                .orElseThrow(UserNotFoundException::new);

        ChatRoom chatRoom = chatRoomService.getOrCreatePrivateRoom(sender, receiver);

        DirectMessage directMessage = DirectMessage.builder()
                .sender(sender)
                .chatRoom(chatRoom)
                .content(request.content())
                .build();

        DirectMessage saved = directMessageRepository.save(directMessage);
        log.info("DM 저장: directMessageId={}", saved.getId());

        NotificationDto notificationDto = notificationService.createDirectMessageInCurrentTransaction(
                receiver.getId(),
                sender.getName(),
                request.content());
        DirectMessageDto directMessageDto = directMessageMapper.toDto(saved, receiver);
        String dmKey = ChatRoom.generateDmKey(authenticatedSenderId, request.receiverId());
        String destination = "/sub/direct-messages_" + dmKey;

        return new DirectMessageCreation(
                directMessageDto,
                notificationDto,
                destination);
    }
}
