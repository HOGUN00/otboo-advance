package codeit.sb06.otboo.comment.controller;

import codeit.sb06.otboo.comment.dto.CommentCreateRequest;
import codeit.sb06.otboo.comment.dto.CommentDto;
import codeit.sb06.otboo.comment.dto.CommentDtoCursorResponse;
import codeit.sb06.otboo.comment.service.CommentService;
import codeit.sb06.otboo.security.resolver.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feeds")
@Tag(name = "Comment", description = "댓글 API")
public class CommentController{

  private final CommentService commentService;

  @Operation(summary = "피드 댓글 등록")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "피드 댓글 등록 성공"),
      @ApiResponse(responseCode = "400", description = "피드 댓글 등록 실패")
  })

  @PostMapping("/{feedId}/comments")
  public ResponseEntity<CommentDto> createComment(
      @Parameter(description = "피드 Id", required = true)
      @CurrentUserId UUID currentUserId,
      @PathVariable UUID feedId,
      @RequestBody CommentCreateRequest commentCreateRequest
  ){

    log.debug("댓글 생성 요청 feedId={}, authorId={}",
        feedId, commentCreateRequest.authorId());

    CommentDto response = commentService.createComment(currentUserId, feedId, commentCreateRequest);

    log.debug("댓글 생성 완료 commentId={}", response.id());

    return ResponseEntity.ok(response);
  }


  @Operation(summary = "피드 댓글 조회")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "피드 댓글 조회 성공"),
      @ApiResponse(responseCode = "400", description = "피드 댓글 조회 실패")
  })

  @GetMapping("/{feedId}/comments")
  public ResponseEntity<CommentDtoCursorResponse> getCommentList(
      @PathVariable UUID feedId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam Integer limit
  ){

    log.debug("댓글 조회 요청 feedId={}, cursor={}, idAfter={}, limit={}",
        feedId, cursor, idAfter, limit);

    CommentDtoCursorResponse response = commentService.getComments(feedId, cursor, idAfter, limit);

    log.debug("댓글 조회 완료 feedId={}, size={}, hasNext={}",
        feedId, response.data().size(), response.hasNext());

    return ResponseEntity.ok(response);
  }
}
