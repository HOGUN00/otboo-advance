package codeit.sb06.otboo.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import codeit.sb06.otboo.exception.user.MailSendException;
import codeit.sb06.otboo.exception.auth.ForbiddenException;
import codeit.sb06.otboo.exception.user.UserAlreadyExistException;
import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.notification.publisher.NotificationEventPublisher;
import codeit.sb06.otboo.profile.service.ProfileServiceImpl;
import codeit.sb06.otboo.security.jwt.JwtRegistry;
import codeit.sb06.otboo.user.dto.UserDto;
import codeit.sb06.otboo.user.dto.request.UserCreateRequest;
import codeit.sb06.otboo.user.dto.request.UserSliceRequest;
import codeit.sb06.otboo.user.dto.response.UserDtoCursorResponse;
import codeit.sb06.otboo.user.dto.request.ChangePasswordRequest;
import codeit.sb06.otboo.user.entity.Role;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.entity.Provider;
import codeit.sb06.otboo.user.repository.UserRepository;
import codeit.sb06.otboo.util.EasyRandomUtil;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.jeasy.random.EasyRandom;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ProfileServiceImpl profileService;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private JwtRegistry jwtRegistry;
    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void createSavesEncodedUserAndCreatesProfile() {
        UserCreateRequest request = new UserCreateRequest("name", "user@example.com", "raw-password");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto result = userService.create(request);

        verify(userRepository).save(argThat(user -> "encoded-password".equals(user.getPassword())));
        verify(profileService).create(any(User.class));
        assertEquals("user@example.com", result.email());
    }

    @Test
    void createThrowsWhenEmailAlreadyExists() {
        UserCreateRequest request = new UserCreateRequest("name", "user@example.com", "raw-password");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user(UUID.randomUUID(), false)));

        assertThrows(UserAlreadyExistException.class, () -> userService.create(request));
    }

    @Test
    void getUsersCursorReturnsSliceResponse() {
        User userA = user(UUID.randomUUID(), false);
        User userB = new User(
            UUID.randomUUID(),
            "admin@example.com",
            "admin",
            Provider.LOCAL,
            Role.ADMIN,
            false,
            LocalDateTime.of(2026, 1, 2, 0, 0),
            LocalDateTime.of(2026, 1, 2, 0, 0),
            null,
            "encoded-password",
            null,
            null
        );
        UserSliceRequest request = new UserSliceRequest(
            null,
            null,
            2,
            "createdAt",
            "DESC",
            null,
            null,
            null
        );
        Slice<User> slice = new SliceImpl<>(List.of(userA, userB), PageRequest.of(0, 2), true);
        when(userRepository.findUsersBySlice(request)).thenReturn(slice);

        UserDtoCursorResponse response = userService.getUsersCursor(request);

        assertEquals(2, response.data().size());
        assertEquals("DESC", response.sortDirection());
    }

    @Test
    void changeUserRoleUpdatesRole() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserDto result = userService.changeUserRole(userId, Role.ADMIN.name());

        assertEquals(Role.ADMIN.name(), result.role());
    }

    @Test
    void changeUserRoleThrowsWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.changeUserRole(userId, Role.ADMIN.name()));
    }

    @Test
    void changeLockStatusWhenLockedTrueInvalidatesJwt() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserDto result = userService.changeLockStatus(userId, true);

        assertEquals(true, result.isLocked());
        verify(jwtRegistry).invalidateJwtInformationByUserId(userId);
    }

    @Test
    void changeLockStatusWhenLockedFalseDoesNotInvalidateJwt() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserDto result = userService.changeLockStatus(userId, false);

        assertEquals(false, result.isLocked());
        verify(jwtRegistry, never()).invalidateJwtInformationByUserId(any(UUID.class));
    }

    @Test
    void changeLockStatusThrowsWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.changeLockStatus(userId, true));
    }

    @Test
    void sendSavesEncodedTemporaryPasswordAndSendsMail() {
        User user = user(UUID.randomUUID(), false);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-temp-password");
        when(mailSender.createMimeMessage()).thenReturn(message);
        ReflectionTestUtils.setField(userService, "from", "no-reply@otboo.com");

        assertDoesNotThrow(() -> userService.send("user@example.com"));

        assertEquals("encoded-temp-password", user.getTemporaryPasswordHash());
        assertNotNull(user.getTemporaryPasswordExpiresAt());
        verify(mailSender).send(message);
    }

    @Test
    void sendThrowsWhenUserMissing() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.send("missing@example.com"));
    }

    @Test
    void sendWrapsMailSenderException() {
        User user = user(UUID.randomUUID(), false);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-temp-password");
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new RuntimeException("smtp fail")).when(mailSender).send(eq(message));
        ReflectionTestUtils.setField(userService, "from", "no-reply@otboo.com");

        assertThrows(MailSendException.class, () -> userService.send("user@example.com"));
    }

    @Test
    void changePasswordEncodesAndUpdatesUserPassword() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");

        userService.changePassword(userId, userId, new ChangePasswordRequest("new-password"));

        assertEquals("encoded-new-password", user.getPassword());
    }

    @Test
    void changePasswordThrowsWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
            () -> userService.changePassword(userId, userId, new ChangePasswordRequest("new-password")));
    }

    @Test
    @DisplayName("인증 사용자는 다른 사용자의 비밀번호를 변경할 수 없다")
    void changePasswordRejectsAnotherUser() {
        UUID currentUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        assertThrows(
            ForbiddenException.class,
            () -> userService.changePassword(
                currentUserId,
                targetUserId,
                easyRandom.nextObject(ChangePasswordRequest.class))
        );
        verify(userRepository, never()).findById(any());
    }

    private User user(UUID id, boolean locked) {
        return new User(
            id,
            "user@example.com",
            "tester",
            Provider.LOCAL,
            Role.USER,
            locked,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 1, 0, 0),
            null,
            "encoded-password",
            null,
            null
        );
    }
}
