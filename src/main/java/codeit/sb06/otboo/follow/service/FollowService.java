package codeit.sb06.otboo.follow.service;

import codeit.sb06.otboo.follow.dto.FollowCreateRequest;
import codeit.sb06.otboo.follow.dto.FollowDto;
import codeit.sb06.otboo.follow.dto.FollowListResponse;
import codeit.sb06.otboo.follow.dto.FollowSummaryDto;
import codeit.sb06.otboo.follow.entity.FollowDirection;
import java.util.UUID;

public interface FollowService {

  FollowDto createFollow(UUID currentUserId, FollowCreateRequest followCreateRequest);

  FollowSummaryDto getFollowSummary(UUID targetId, UUID myId);

  FollowListResponse getFollowList(
      FollowDirection direction,
      UUID userId,
      String cursor,
      UUID idAfter,
      int limit,
      String nameLike);

  void deleteFollow(UUID currentUserId, UUID followId);
}
