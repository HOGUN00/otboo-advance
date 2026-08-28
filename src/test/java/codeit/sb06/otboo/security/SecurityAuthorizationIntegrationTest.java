package codeit.sb06.otboo.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static codeit.sb06.otboo.security.SecurityTestFixtures.user;
import static codeit.sb06.otboo.security.SecurityTestFixtures.userDto;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import codeit.sb06.otboo.clothes.repository.ClothesRepository;
import codeit.sb06.otboo.OtbooApplication;
import codeit.sb06.otboo.config.SecurityConfig;
import codeit.sb06.otboo.config.WebMvcConfig;
import codeit.sb06.otboo.feed.controller.FeedController;
import codeit.sb06.otboo.feed.entity.Feed;
import codeit.sb06.otboo.feed.repository.FeedLikeRepository;
import codeit.sb06.otboo.feed.repository.FeedRepository;
import codeit.sb06.otboo.feed.service.FeedService;
import codeit.sb06.otboo.follow.repository.FollowRepository;
import codeit.sb06.otboo.notification.publisher.NotificationEventPublisher;
import codeit.sb06.otboo.profile.service.S3StorageService;
import codeit.sb06.otboo.security.handler.LoginFailureHandler;
import codeit.sb06.otboo.security.handler.CustomAuthenticationEntryPoint;
import codeit.sb06.otboo.security.handler.OAuth2FailureHandler;
import codeit.sb06.otboo.security.jwt.JwtAuthenticationFilter;
import codeit.sb06.otboo.security.jwt.JwtLoginSuccessHandler;
import codeit.sb06.otboo.security.jwt.JwtLogoutHandler;
import codeit.sb06.otboo.security.jwt.JwtRegistry;
import codeit.sb06.otboo.security.jwt.JwtTokenProvider;
import codeit.sb06.otboo.security.resolver.CurrentUserIdArgumentResolver;
import codeit.sb06.otboo.security.resolver.RoleAuthorizationInterceptor;
import codeit.sb06.otboo.security.user.OtbooUserDetails;
import codeit.sb06.otboo.security.user.TemporaryPasswordAuthenticationProvider;
import codeit.sb06.otboo.user.entity.Role;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import codeit.sb06.otboo.user.service.CustomOAuth2UserService;
import codeit.sb06.otboo.user.service.CustomOidcUserService;
import codeit.sb06.otboo.weather.repository.WeatherRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = FeedController.class,
    properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-secret"
    }
)
@AutoConfigureMockMvc
@ContextConfiguration(classes = OtbooApplication.class)
@Import({
    SecurityConfig.class,
    WebMvcConfig.class,
    CurrentUserIdArgumentResolver.class,
    RoleAuthorizationInterceptor.class,
    CustomAuthenticationEntryPoint.class,
    JwtAuthenticationFilter.class,
    FeedService.class
})
class SecurityAuthorizationIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean private FeedRepository feedRepository;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private WeatherRepository weatherRepository;
  @MockitoBean private ClothesRepository clothesRepository;
  @MockitoBean private FeedLikeRepository feedLikeRepository;
  @MockitoBean private FollowRepository followRepository;
  @MockitoBean private NotificationEventPublisher notificationEventPublisher;
  @MockitoBean private S3StorageService s3StorageService;
  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private JwtRegistry jwtRegistry;
  @MockitoBean private UserDetailsService userDetailsService;
  @MockitoBean private JwtLoginSuccessHandler jwtLoginSuccessHandler;
  @MockitoBean private JwtLogoutHandler jwtLogoutHandler;
  @MockitoBean private LoginFailureHandler loginFailureHandler;
  @MockitoBean private OAuth2FailureHandler oauth2FailureHandler;
  @MockitoBean private TemporaryPasswordAuthenticationProvider temporaryPasswordAuthenticationProvider;
  @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
  @MockitoBean private CustomOidcUserService customOidcUserService;

  @Test
  @DisplayName("미인증 사용자는 보호 API를 호출할 수 없다")
  void unauthenticatedUserCannotCallProtectedApi() throws Exception {
    mockMvc.perform(get("/api/feeds"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.exceptionName").value("InsufficientAuthenticationException"))
        .andExpect(jsonPath("$.message").isNotEmpty())
        .andExpect(jsonPath("$.details").isMap());
  }

  @Test
  @DisplayName("일반 사용자는 다른 사용자의 피드를 삭제할 수 없다")
  void userCannotDeleteAnotherUsersFeed() throws Exception {
    UUID currentUserId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();
    User owner = user(UUID.randomUUID(), Role.USER);
    User currentUser = user(currentUserId, Role.USER);
    Feed feed = feed(owner);
    when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));
    when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));

    mockMvc.perform(delete("/api/feeds/{feedId}", feedId)
            .with(authentication(testAuthentication(currentUserId, Role.USER)))
            .with(csrf()))
        .andExpect(status().isForbidden());

    verify(feedRepository, never()).delete(feed);
  }

  @Test
  @DisplayName("피드 작성자는 본인의 피드를 삭제할 수 있다")
  void ownerCanDeleteOwnFeed() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();
    Feed feed = feed(user(ownerId, Role.USER));
    when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));

    mockMvc.perform(delete("/api/feeds/{feedId}", feedId)
            .with(authentication(testAuthentication(ownerId, Role.USER)))
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(feedRepository).delete(feed);
  }

  @Test
  @DisplayName("관리자는 다른 사용자의 피드를 삭제할 수 있다")
  void adminCanDeleteAnotherUsersFeed() throws Exception {
    UUID adminId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();
    User admin = user(adminId, Role.ADMIN);
    Feed feed = feed(user(UUID.randomUUID(), Role.USER));
    when(feedRepository.findById(feedId)).thenReturn(Optional.of(feed));
    when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

    mockMvc.perform(delete("/api/feeds/{feedId}", feedId)
            .with(authentication(testAuthentication(adminId, Role.ADMIN)))
            .with(csrf()))
        .andExpect(status().isNoContent());

    verify(feedRepository).delete(feed);
  }

  private Authentication testAuthentication(UUID userId, Role role) {
    OtbooUserDetails principal = new OtbooUserDetails(
        userDto(userId, role), "password", Map.of());
    return UsernamePasswordAuthenticationToken.authenticated(
        principal, principal.getPassword(), principal.getAuthorities());
  }

  private Feed feed(User owner) {
    Feed feed = mock(Feed.class);
    when(feed.getUser()).thenReturn(owner);
    return feed;
  }
}
