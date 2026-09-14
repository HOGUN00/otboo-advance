package codeit.sb06.otboo.message.service.impl;

import codeit.sb06.otboo.exception.message.ChatRoomNotFoundException;
import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.message.dto.DirectMessageCreation;
import codeit.sb06.otboo.message.dto.DirectMessageDto;
import codeit.sb06.otboo.message.dto.request.DirectMessageCreateRequest;
import codeit.sb06.otboo.message.dto.response.DirectMessageDtoCursorResponse;
import codeit.sb06.otboo.message.entity.ChatRoom;
import codeit.sb06.otboo.message.entity.DirectMessage;
import codeit.sb06.otboo.message.mapper.DirectMessageMapper;
import codeit.sb06.otboo.message.publisher.DirectMessageRedisPublisher;
import codeit.sb06.otboo.message.repository.ChatRoomRepository;
import codeit.sb06.otboo.message.repository.DirectMessageRepository;
import codeit.sb06.otboo.message.service.DirectMessageService;
import codeit.sb06.otboo.notification.publisher.RedisNotificationPublisher;
import codeit.sb06.otboo.notification.service.NotificationCacheService;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DirectMessageServiceImpl implements DirectMessageService {

    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;
    private final DirectMessageMapper directMessageMapper;
    private final ChatRoomRepository chatRoomRepository;
    private final DirectMessageRedisPublisher directMessageRedisPublisher;
    private final NotificationCacheService notificationCacheService;
    private final RedisNotificationPublisher redisNotificationPublisher;
    private final DirectMessageCreatorService creatorService;

    @Override
    public DirectMessageDto create(UUID authenticatedSenderId, DirectMessageCreateRequest request) {
        DirectMessageCreation creation = creatorService.create(authenticatedSenderId, request);
        directMessageRedisPublisher.publish(
                creation.directMessageDto(),
                creation.destination());
        notificationCacheService.save(creation.notificationDto());
        redisNotificationPublisher.publish(creation.notificationDto());

        return creation.directMessageDto();
    }

    @Override
    @Transactional(readOnly = true)
    public DirectMessageDtoCursorResponse getDirectMessages(UUID myUserId, UUID senderId, LocalDateTime cursor, UUID idAfter, int limit) {

        String dmKey = ChatRoom.generateDmKey(myUserId, senderId);

        ChatRoom chatRoom = chatRoomRepository.findByDmKey(dmKey)
                .orElseThrow(ChatRoomNotFoundException::new);

        User receiver = userRepository.findById(myUserId)
                .orElseThrow(UserNotFoundException::new);

        Slice<DirectMessage> directMessages;

        if(cursor == null && idAfter == null) {
            directMessages = directMessageRepository.findFirstPageByChatRoom(chatRoom, PageRequest.of(0, limit));
        } else {
            directMessages = directMessageRepository.findByChatRoomWithCursor(
                    chatRoom,
                    cursor,
                    idAfter,
                    // pageable로 원하는 개수만큼 조회
                    PageRequest.of(0, limit)
            );
        }

        return directMessageMapper.toDtoCursorResponse(directMessages, receiver);
    }
}
