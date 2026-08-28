package codeit.sb06.otboo.follow.service;

import codeit.sb06.otboo.exception.follow.FollowCancelFailException;
import codeit.sb06.otboo.exception.follow.FollowNotFoundException;
import codeit.sb06.otboo.exception.profile.ProfileNotFoundException;
import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.follow.dto.FollowCreateRequest;
import codeit.sb06.otboo.follow.dto.FollowDto;
import codeit.sb06.otboo.follow.dto.FollowListResponse;
import codeit.sb06.otboo.follow.dto.FollowSummaryDto;
import codeit.sb06.otboo.follow.dto.FolloweeDto;
import codeit.sb06.otboo.follow.dto.FollowerDto;
import codeit.sb06.otboo.follow.entity.Follow;
import codeit.sb06.otboo.follow.entity.FollowDirection;
import codeit.sb06.otboo.follow.repository.FollowRepository;
import codeit.sb06.otboo.profile.entity.Profile;
import codeit.sb06.otboo.profile.repository.ProfileRepository;
import codeit.sb06.otboo.notification.publisher.NotificationEventPublisher;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class BasicFollowService implements FollowService {

  private final FollowRepository followRepository;
  private final UserRepository userRepository;
  private final ProfileRepository profileRepository;
  private final NotificationEventPublisher notificationEventPublisher;

  @Transactional
  @Override
  public FollowDto createFollow(UUID currentUserId, FollowCreateRequest followCreateRequest) {

    User follower = userRepository.findById(currentUserId)
        .orElseThrow(UserNotFoundException::new);
    User followee = userRepository.findById(followCreateRequest.followeeId())
        .orElseThrow(UserNotFoundException::new);

    Follow follow = Follow.of(follower,followee);

    // 저장
    followRepository.save(follow);

    Profile followerProfile = profileRepository.findByUserId(follower)
        .orElseThrow(ProfileNotFoundException::new);
    Profile followeeProfile = profileRepository.findByUserId(followee)
        .orElseThrow(ProfileNotFoundException::new);

    // 팔로우 하는 사람의 팔로잉 수 증가
    followerProfile.increaseFollowingCount();
    // 팔로우 당하는 사람의 팔로워 수 증가
    followeeProfile.increaseFollowerCount();

    FolloweeDto followeeDto = new FolloweeDto(followee.getId(),followee.getName(), followee.getProfileImageUrl());
    FollowerDto followerDto = new FollowerDto(follower.getId(), follower.getName(),follower.getProfileImageUrl());

    log.debug("팔로우 생성 완료 followId={}", follow.getId());

    notificationEventPublisher.publishFollowedEvent(followee.getId(), follower.getName());

    return FollowDto.of(follow, followeeDto, followerDto);
  }

  @Override
  public FollowSummaryDto getFollowSummary(UUID targetId, UUID myId) {

    log.info("팔로우 요약 정보 실행 targetId={}, myId={}", targetId, myId);

    User target = userRepository.findById(targetId)
        .orElseThrow(UserNotFoundException::new);

    log.debug("사용자 찾음 - id={}, name={}", target.getId(), target.getName());


    Profile profile = profileRepository.findByUserId(target)
        .orElseThrow(ProfileNotFoundException::new);

    //팔로위 팔로우 당하는거, 팔로워 팔로우 거는거

    //팔로워 수. 팔로워? -> 내가 팔로우하는
    Long followerCount = profile.getFollowerCount();
    //팔로잉 수  팔로잉 -> 나를 팔로우하는
    Long followingCount = profile.getFollowingCount();

    // 나에의해 팔로우되었는지
    Optional<Follow>  followedByMe= followRepository.findByFollowerIdAndFolloweeId(myId, targetId);

    // 나를 팔로우하는지
    Optional<Follow> followingMe= followRepository.findByFollowerIdAndFolloweeId(targetId, myId);

    log.info("팔로우 요약 정보 조회 성공 - targetId={}", targetId);

    return FollowSummaryDto.of(
        targetId,
        followerCount,
        followingCount,
        followedByMe,
        followingMe

    );
  }

  @Override
  public FollowListResponse getFollowList(FollowDirection direction, UUID userId, String cursor,
      UUID idAfter, int limit, String nameLike) {

    LocalDateTime lastCreatedAt = null;

    if(cursor != null) {
      lastCreatedAt = LocalDateTime.parse(cursor);
    }

    List<Follow> followList =
        followRepository.findByCursor(
            direction,
            userId,
            lastCreatedAt,
            idAfter,
            limit+1,
            nameLike
        );

    boolean hasNext = followList.size() > limit;

    if(hasNext) {
      followList = followList.subList(0, limit);
    }

    String nextCursor = null;
    UUID nextIdAfter = null;

    if(hasNext&&!followList.isEmpty()) {
      Follow lastFollow = followList.get(followList.size()-1);
      LocalDateTime createdAt = lastFollow.getCreatedAt();

      nextCursor = createdAt.toString();
      nextIdAfter = lastFollow.getId();
    }

    List<FollowDto> data = followList.stream()
        .map(follow -> FollowDto.of(
            follow,
            new FolloweeDto(
                follow.getFollowee().getId(),
                follow.getFollowee().getName(),
                follow.getFollowee().getProfileImageUrl()
            ),
            new FollowerDto(
                follow.getFollower().getId(),
                follow.getFollower().getName(),
                follow.getFollower().getProfileImageUrl()
            )
        ))
        .toList();

    Long totalCount =
        followRepository.countByCondition(
            direction,
            userId,
            nameLike
        );

    log.debug("팔로우 목록 조회 완료 size={}, hasNext={}", data.size(), hasNext);

    return new FollowListResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "createdAt",
        "DESCENDING"
    );
  }

  @Transactional
  @Override
  public void deleteFollow(UUID followId) {

    Follow follow =  followRepository.findById(followId)
        .orElseThrow(() -> new FollowCancelFailException(new FollowNotFoundException()));

    try {

      User follower = follow.getFollower();
      User followee = follow.getFollowee();

      Profile followerProfile = profileRepository.findByUserId(follower)
              .orElseThrow(ProfileNotFoundException::new);

      Profile followeeProfile = profileRepository.findByUserId(followee)
              .orElseThrow(ProfileNotFoundException::new);

      followRepository.deleteById(followId);

      log.debug("팔로우 삭제 완료 followId={}", followId);

      followerProfile.decreaseFollowingCount();
      followeeProfile.decreaseFollowerCount();

  } catch (Exception e) {
    throw new FollowCancelFailException(e);
    }
  }
}
