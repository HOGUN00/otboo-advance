package codeit.sb06.otboo.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static codeit.sb06.otboo.security.SecurityTestFixtures.userDto;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.sb06.otboo.profile.dto.LocationDto;
import codeit.sb06.otboo.profile.dto.ProfileDto;
import codeit.sb06.otboo.profile.entity.Location;
import codeit.sb06.otboo.user.dto.UserDto;
import codeit.sb06.otboo.user.dto.response.UserDtoCursorResponse;
import codeit.sb06.otboo.user.entity.Role;
import codeit.sb06.otboo.config.WebMvcConfig;
import codeit.sb06.otboo.security.resolver.CurrentUserIdArgumentResolver;
import codeit.sb06.otboo.security.handler.LoginFailureHandler;
import codeit.sb06.otboo.security.resolver.RoleAuthorizationInterceptor;
import codeit.sb06.otboo.security.jwt.JwtAuthenticationFilter;
import codeit.sb06.otboo.security.jwt.JwtLoginSuccessHandler;
import codeit.sb06.otboo.security.jwt.JwtLogoutHandler;
import codeit.sb06.otboo.security.jwt.JwtTokenProvider;
import codeit.sb06.otboo.security.user.OtbooUserDetails;
import codeit.sb06.otboo.profile.service.ProfileServiceImpl;
import codeit.sb06.otboo.user.service.UserServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockMultipartFile;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    WebMvcConfig.class,
    RoleAuthorizationInterceptor.class,
    CurrentUserIdArgumentResolver.class
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserServiceImpl userServiceImpl;

    @MockitoBean
    private ProfileServiceImpl profileServiceImpl;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtLoginSuccessHandler jwtLoginSuccessHandler;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtLogoutHandler jwtLogoutHandler;

    @MockitoBean
    private LoginFailureHandler loginFailureHandler;

    @TestConfiguration
    static class RoleHierarchyTestConfig {
        @Bean
        RoleHierarchy roleHierarchy() {
            return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(Role.ADMIN.name())
                .implies(Role.USER.name())
                .build();
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReturnsUserDto() throws Exception {
        UserDto userDto = new UserDto(
            UUID.randomUUID(),
            "user@example.com",
            LocalDateTime.of(2026, 1, 1, 0, 0),
            Role.USER.name(),
            false
        );
        when(userServiceImpl.create(any())).thenReturn(userDto);

        mockMvc.perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"name\",\"email\":\"user@example.com\",\"password\":\"pass\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.isLocked").value(false));
    }

    @Test
    void getReturnsCursorResponse() throws Exception {
        setAuthentication(Role.ADMIN);
        UserDto userDto = new UserDto(
            UUID.randomUUID(),
            "user@example.com",
            LocalDateTime.of(2026, 1, 1, 0, 0),
            Role.USER.name(),
            false
        );
        UserDtoCursorResponse response = new UserDtoCursorResponse(
            List.of(userDto),
            "2026-01-01T00:00",
            userDto.id().toString(),
            true,
            1,
            "createdAt",
            "DESC"
        );
        when(userServiceImpl.getUsersCursor(any())).thenReturn(response);

        mockMvc.perform(
                get("/api/users")
                    .param("limit", "1")
                    .param("sortBy", "createdAt")
                    .param("sortDirection", "DESC")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].email").value("user@example.com"))
            .andExpect(jsonPath("$.nextCursor").value("2026-01-01T00:00"))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.sortBy").value("createdAt"))
            .andExpect(jsonPath("$.sortDirection").value("DESC"));
    }

    @Test
    void getReturnsForbiddenWhenUserRoleIsNotAdmin() throws Exception {
        setAuthentication(Role.USER);
        mockMvc.perform(
                get("/api/users")
                    .param("limit", "1")
                    .param("sortBy", "createdAt")
                    .param("sortDirection", "DESC")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void updateRoleReturnsUserDto() throws Exception {
        setAuthentication(Role.ADMIN);
        UserDto userDto = new UserDto(
            UUID.randomUUID(),
            "admin@example.com",
            LocalDateTime.of(2026, 1, 1, 0, 0),
            Role.ADMIN.name(),
            false
        );
        when(userServiceImpl.changeUserRole(any(), any())).thenReturn(userDto);

        mockMvc.perform(
                patch("/api/users/{userId}/role", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"role\":\"ADMIN\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void updateLockReturnsUserDto() throws Exception {
        setAuthentication(Role.ADMIN);
        UserDto userDto = new UserDto(
            UUID.randomUUID(),
            "user@example.com",
            LocalDateTime.of(2026, 1, 1, 0, 0),
            Role.USER.name(),
            true
        );
        when(userServiceImpl.changeLockStatus(any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(userDto);

        mockMvc.perform(
                patch("/api/users/{userId}/lock", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"locked\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isLocked").value(true));
    }

    @Test
    void changePasswordReturnsNoContent() throws Exception {
        UUID userId = UUID.randomUUID();
        setAuthentication(userId, Role.USER);

        mockMvc.perform(
                patch("/api/users/{userId}/password", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"new-password\"}")
            )
            .andExpect(status().isNoContent());

        verify(userServiceImpl).changePassword(eq(userId), eq(userId), any());
    }

    @Test
    void updateProfileAcceptsMultipartFormData() throws Exception {
        UUID userId = UUID.randomUUID();
        setAuthentication(userId, Role.USER);
        ProfileDto profileDto = new ProfileDto(
            userId,
            "new-name",
            "ETC",
            "2000-01-01",
            LocationDto.from(new Location(UUID.randomUUID(), 37.5, 126.9, 60, 127, List.of("seoul"), null)),
            3,
            "s3-key"
        );
        when(profileServiceImpl.updateProfile(eq(userId), eq(userId), any(), isNull())).thenReturn(profileDto);

        MockMultipartFile profile = new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            """
            {
              "name":"new-name",
              "gender":"ETC",
              "birthDate":"2000-01-01",
              "locationDto":{"latitude":37.5,"longitude":126.9,"x":60,"y":127,"locationNames":["seoul"]},
              "temperatureSensitivity":3
            }
            """.getBytes()
        );

        mockMvc.perform(
                multipart("/api/users/{userId}/profiles", userId)
                    .file(profile)
                    .with(request -> {
                        request.setMethod("PATCH");
                        return request;
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.name").value("new-name"))
            .andExpect(jsonPath("$.gender").value("ETC"));
    }

    private void setAuthentication(Role role) {
        setAuthentication(UUID.randomUUID(), role);
    }

    private void setAuthentication(UUID userId, Role role) {
        OtbooUserDetails principal = new OtbooUserDetails(
            userDto(userId, role), "password", Map.of());
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
            );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
