package codeit.sb06.otboo.follow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static codeit.sb06.otboo.security.SecurityTestFixtures.userDto;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.sb06.otboo.exception.follow.SelfFollowDeniedException;
import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.follow.dto.FollowCreateRequest;
import codeit.sb06.otboo.follow.dto.FollowDto;
import codeit.sb06.otboo.follow.dto.FolloweeDto;
import codeit.sb06.otboo.follow.dto.FollowerDto;
import codeit.sb06.otboo.follow.service.FollowService;
import codeit.sb06.otboo.security.jwt.JwtAuthenticationFilter;
import codeit.sb06.otboo.security.jwt.JwtTokenProvider;
import codeit.sb06.otboo.security.user.OtbooUserDetails;
import codeit.sb06.otboo.user.entity.Role;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FollowController.class)
@AutoConfigureMockMvc(addFilters = false)
public class FollowCreateResponseTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  RoleHierarchy roleHierarchy;

  @MockitoBean
  JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  JwtAuthenticationFilter jwtAuthenticationFilter;

  @MockitoBean
  FollowService followService;

  @Autowired
  ObjectMapper objectMapper;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void follow_success_201response() throws Exception {

    // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      UUID followId = UUID.randomUUID();

      FollowCreateRequest request = new FollowCreateRequest(followeeId, followerId);

      FollowDto response = new FollowDto(
          followId,
          new FolloweeDto(followeeId, "테스트 팔로위", "test1"),
          new FollowerDto(followerId, "테스트 팔로워", "test2")
      );

      authenticate(followerId);
      when(followService.createFollow(eq(followerId), any(FollowCreateRequest.class)))
          .thenReturn(response);

      //then
      mockMvc.perform(post("/api/follows")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(followId.toString()))
          .andExpect(jsonPath("$.followee.userId").value(followeeId.toString()))
          .andExpect(jsonPath("$.follower.userId").value(followerId.toString()));
    }

  @Test
  void follow_fail_400response_sameUser() throws Exception {

    UUID userId = UUID.randomUUID();

    UUID followerId = userId;
    UUID followeeId = userId;

    FollowCreateRequest request = new FollowCreateRequest(followeeId, followerId);

    authenticate(followerId);
    when(followService.createFollow(eq(followerId), any(FollowCreateRequest.class)))
        .thenThrow(new SelfFollowDeniedException());

    mockMvc.perform(post("/api/follows")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("SelfFollowDeniedException"))
        .andExpect(jsonPath("$.message")
            .value("자기 자신을 팔로우 할 수 없습니다."))
        .andExpect(jsonPath("$.details").exists());

  }

  @Test
  void follow_fail_404response_emptyUser() throws Exception {

    //given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(followeeId, followerId);

    authenticate(followerId);
    when(followService.createFollow(eq(followerId), any(FollowCreateRequest.class)))
        .thenThrow(new UserNotFoundException());

    //then
    mockMvc.perform(post("/api/follows")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName")
            .value("UserNotFoundException"))
        .andExpect(jsonPath("$.message")
            .value("User not found"))
        .andExpect(jsonPath("$.details").exists());
  }

  private void authenticate(UUID userId) {
    OtbooUserDetails principal = new OtbooUserDetails(
        userDto(userId, Role.USER), "password", Map.of());
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, principal.getPassword(), principal.getAuthorities()));
  }
}
