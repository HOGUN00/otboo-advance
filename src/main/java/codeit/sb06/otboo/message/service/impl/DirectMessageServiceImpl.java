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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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
        publishDirectMessage(creation);
        cacheNotification(creation);
        publishNotification(creation);

        return creation.directMessageDto();
    }

    private void publishDirectMessage(DirectMessageCreation creation) {
        try {
            directMessageRedisPublisher.publish(
                    creation.directMessageDto(),
                    creation.destination());
        } catch (RuntimeException exception) {
            log.error(
                    "DM Redis Stream 처리 실패: directMessageId={}",
                    creation.directMessageDto().id(),
                    exception);
        }
    }

    private void cacheNotification(DirectMessageCreation creation) {
        try {
            notificationCacheService.save(creation.notificationDto());
        } catch (RuntimeException exception) {
            log.error(
                    "알림 Redis Cache 저장 실패: notificationId={}",
                    creation.notificationDto().id(),
                    exception);
        }
    }

    private void publishNotification(DirectMessageCreation creation) {
        try {
            redisNotificationPublisher.publish(creation.notificationDto());
        } catch (RuntimeException exception) {
            log.error(
                    "알림 Redis Stream 처리 실패: notificationId={}",
                    creation.notificationDto().id(),
                    exception);
        }
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
