package codeit.sb06.otboo.comment.service;

import codeit.sb06.otboo.comment.dto.CommentCreateRequest;
import codeit.sb06.otboo.comment.dto.CommentDto;
import codeit.sb06.otboo.comment.dto.CommentDtoCursorResponse;
import java.util.UUID;

public interface CommentService {

  CommentDto createComment(UUID currentUserId, UUID feedId, CommentCreateRequest commentCreateRequest);

  CommentDtoCursorResponse getComments(UUID feedId, String cursor, UUID idAfter, Integer limit);
}
