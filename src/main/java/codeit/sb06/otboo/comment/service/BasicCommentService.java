package codeit.sb06.otboo.comment.service;

import codeit.sb06.otboo.comment.dto.CommentDtoCursorResponse;
import codeit.sb06.otboo.comment.repository.CommentRepository;
import codeit.sb06.otboo.comment.dto.AuthorDto;
import codeit.sb06.otboo.comment.dto.CommentCreateRequest;
import codeit.sb06.otboo.comment.dto.CommentDto;
import codeit.sb06.otboo.comment.entity.Comment;
import codeit.sb06.otboo.exception.feed.FeedNotFoundException;
import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.feed.entity.Feed;
import codeit.sb06.otboo.feed.repository.FeedRepository;
import codeit.sb06.otboo.notification.publisher.NotificationEventPublisher;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class BasicCommentService implements CommentService {

  private final CommentRepository commentRepository;
  private final FeedRepository feedRepository;
  private final UserRepository userRepository;
  private final NotificationEventPublisher notificationEventPublisher;

  @Transactional
  @Override
  public CommentDto createComment(UUID currentUserId, UUID feedId, CommentCreateRequest commentCreateRequest) {

    Feed feed = feedRepository.findById(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    User author = userRepository.findById(currentUserId)
        .orElseThrow(UserNotFoundException::new);

    Comment comment = Comment.builder()
        .content(commentCreateRequest.content())
        .feed(feed)
        .user(author)
        .build()
        ;

    Comment savedComment = commentRepository.save(comment);

    log.debug("댓글 생성 완료 commentId={}, feedId={}, authorId = {}", savedComment.getId(), feedId, author.getId());

    feed.incrementCommentCount();

    if(!feed.getUser().getId().equals(author.getId())) {
      notificationEventPublisher.publishFeedCommentedEvent(
              feed.getUser().getId(),
              author.getName(),
              feed.getContent().substring(0, Math.min(10, feed.getContent().length())),
              commentCreateRequest.content());
    }

    return CommentDto.of(
        savedComment,
        AuthorDto.of(author)
    );
  }

  @Override
  public CommentDtoCursorResponse getComments(UUID feedId, String cursor, UUID idAfter,
      Integer limit) {

    Feed feed = feedRepository.findById(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    LocalDateTime lastCreatedAt = null;

    if(cursor!= null){
      lastCreatedAt = LocalDateTime.parse(cursor);
    }

    List<Comment> commentList = commentRepository.findCommentListByCursor(feedId,lastCreatedAt, idAfter, limit+1);

    boolean hasNext = commentList.size() > limit;

    if(hasNext) {
     commentList = commentList.subList(0, limit);
    }

    String nextCursor = null;
    UUID nextIdAfter = null;

    if(hasNext && !commentList.isEmpty()) {
      Comment lastComment  = commentList.get(commentList.size()-1);

      LocalDateTime createdAt = lastComment.getCreatedAt();
      if(createdAt == null){
        throw new IllegalArgumentException("널이 되지 않는거 명시해줬음");
      }
      nextCursor = createdAt.toString();
      nextIdAfter = lastComment.getId();
    }


    List<CommentDto> data = commentList.stream()
        .map(c -> CommentDto.of(c,AuthorDto.of(c.getUser())))
        .toList();

    Long totalCount = feed.getCommentCount();

    log.debug("댓글 목록 조회 완료: feedId = {}, listSize = {}, hasNext = {}", feedId, data.size(), hasNext);

    return new CommentDtoCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "createdAt",
        "DESCENDING"
    );

  }
}
