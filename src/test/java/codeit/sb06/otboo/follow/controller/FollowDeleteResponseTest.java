package codeit.sb06.otboo.follow.controller;


import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static codeit.sb06.otboo.security.SecurityTestFixtures.userDto;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.sb06.otboo.exception.follow.FollowCancelFailException;
import codeit.sb06.otboo.follow.service.FollowService;
import codeit.sb06.otboo.security.jwt.JwtAuthenticationFilter;
import codeit.sb06.otboo.security.jwt.JwtTokenProvider;
import codeit.sb06.otboo.security.user.OtbooUserDetails;
import codeit.sb06.otboo.user.entity.Role;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FollowController.class)
@AutoConfigureMockMvc(addFilters = false)
public class FollowDeleteResponseTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private RoleHierarchy roleHierarchy;

  @MockitoBean
  private JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @MockitoBean
  private FollowService followService;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }


  @Test
  void deleteFollow_success_204() throws Exception {
    // given
    UUID followId = UUID.randomUUID();
    UUID currentUserId = authenticate();

    doNothing().when(followService).deleteFollow(currentUserId, followId);

    //then
    mockMvc.perform(delete("/api/follows/{followId}", followId))
        .andExpect(status().isNoContent());

    verify(followService).deleteFollow(currentUserId, followId);
  }

  @Test
  void deleteFollow_fail_400() throws Exception {
    // given
    UUID followId = UUID.randomUUID();
    UUID currentUserId = authenticate();

    doThrow(new FollowCancelFailException())
        .when(followService)
        .deleteFollow(currentUserId, followId);

    // then
    mockMvc.perform(delete("/api/follows/{followId}", followId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("FollowCancelFailException"))
        .andExpect(jsonPath("$.message")
            .value("팔로우 취소 실패"))
        .andExpect(jsonPath("$.details").exists());
  }

  private UUID authenticate() {
    UUID userId = UUID.randomUUID();
    OtbooUserDetails principal = new OtbooUserDetails(
        userDto(userId, Role.USER), "password", Map.of());
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, principal.getPassword(), principal.getAuthorities()));
    return userId;
  }

}
