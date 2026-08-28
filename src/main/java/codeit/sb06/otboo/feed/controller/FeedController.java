package codeit.sb06.otboo.feed.controller;

import codeit.sb06.otboo.feed.dto.FeedCreateRequest;
import codeit.sb06.otboo.feed.dto.FeedDto;
import codeit.sb06.otboo.feed.dto.FeedDtoCursorRequest;
import codeit.sb06.otboo.feed.dto.FeedDtoCursorResponse;
import codeit.sb06.otboo.feed.dto.FeedUpdateRequest;
import codeit.sb06.otboo.feed.service.FeedService;
import codeit.sb06.otboo.security.resolver.CurrentUserId;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedController {

  private final FeedService service;

  @PostMapping
  public ResponseEntity<FeedDto> createFeed(
          @CurrentUserId UUID currentUserId,
          @RequestBody FeedCreateRequest request
  ) {
    return new ResponseEntity<>(service.create(currentUserId,request), HttpStatus.CREATED);
  }

  @GetMapping
  public ResponseEntity<FeedDtoCursorResponse> getFeed(
      @CurrentUserId UUID id,
      @Valid @ModelAttribute FeedDtoCursorRequest request
  ) {
    return ResponseEntity.ok(service.getFeeds(id, request));
  }

  @DeleteMapping("/{feedId}")
  public ResponseEntity<Void> deleteFeed(@PathVariable UUID feedId) {
    service.delete(feedId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{feedId}")
  public ResponseEntity<FeedDto> updateFeed(
      @PathVariable UUID feedId,
      @RequestBody FeedUpdateRequest content,
      @CurrentUserId UUID currentUserId
  ) {
    return ResponseEntity.ok(service.update(feedId, currentUserId, content));
  }

  @PostMapping("/{feedId}/like")
  public ResponseEntity<Void> likeFeed(
      @PathVariable UUID feedId,
      @CurrentUserId UUID currentUserId
  ) {
    service.like(feedId, currentUserId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{feedId}/like")
  public ResponseEntity<Void> unlikeFeed(
      @PathVariable UUID feedId,
      @CurrentUserId UUID currentUserId
  ) {
    service.unlike(feedId, currentUserId);
    return ResponseEntity.noContent().build();
  }
}
