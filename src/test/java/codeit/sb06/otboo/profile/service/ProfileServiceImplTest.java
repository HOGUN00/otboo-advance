package codeit.sb06.otboo.profile.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import codeit.sb06.otboo.exception.profile.ProfileNotFoundException;
import codeit.sb06.otboo.exception.auth.ForbiddenException;
import codeit.sb06.otboo.exception.profile.S3UploadFailedException;
import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.profile.dto.LocationDto;
import codeit.sb06.otboo.profile.dto.ProfileDto;
import codeit.sb06.otboo.profile.dto.ProfileUpdateRequest;
import codeit.sb06.otboo.profile.entity.Gender;
import codeit.sb06.otboo.profile.entity.Location;
import codeit.sb06.otboo.profile.entity.Profile;
import codeit.sb06.otboo.profile.repository.LocationRepository;
import codeit.sb06.otboo.profile.repository.ProfileRepository;
import codeit.sb06.otboo.user.entity.Role;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.entity.Provider;
import codeit.sb06.otboo.user.repository.UserRepository;
import codeit.sb06.otboo.util.EasyRandomUtil;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.jeasy.random.EasyRandom;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    private final EasyRandom easyRandom = EasyRandomUtil.getRandom();

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private ProfileServiceImpl profileService;

    @Test
    void createBuildsProfileAndReturnsDto() {
        User user = createUser();
        Profile savedProfile = createProfile(user);

        when(userRepository.findByEmail(eq(user.getEmail()))).thenReturn(Optional.of(user));
        when(profileRepository.save(any(Profile.class))).thenReturn(savedProfile);
        when(locationRepository.findByProfile(savedProfile))
            .thenReturn(Optional.of(Location.from(emptyLocationDto(), savedProfile)));

        ProfileDto result = profileService.create(user);

        verify(profileRepository).save(any(Profile.class));
        assertNotNull(result);
        assertEquals(user.getId(), result.userId());
        assertEquals("name", result.name());
        assertEquals(List.of(), result.location().locationNames());
    }

    @Test
    void createThrowsWhenUserNotFound() {
        User user = createUser();
        when(userRepository.findByEmail(eq(user.getEmail()))).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> profileService.create(user));
    }

    @Test
    void getProfileByUserIdReturnsProfile() {
        User user = createUser();
        Profile profile = createProfile(user);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user)).thenReturn(Optional.of(profile));
        when(locationRepository.findByProfile(profile))
            .thenReturn(Optional.of(Location.from(createLocationDto(), profile)));

        ProfileDto result = profileService.getProfileByUserId(user.getId());

        assertEquals(user.getId(), result.userId());
        assertEquals("name", result.name());
        assertEquals(List.of("seoul", "gangnam"), result.location().locationNames());
    }

    @Test
    void getProfileByUserIdThrowsWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> profileService.getProfileByUserId(userId));
    }

    @Test
    void getProfileByUserIdThrowsWhenProfileMissing() {
        User user = createUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user)).thenReturn(Optional.empty());

        assertThrows(ProfileNotFoundException.class, () -> profileService.getProfileByUserId(user.getId()));
    }

    @Test
    void updateProfileCreatesLocationAndUploadsImage() {
        User user = createUser();
        Profile profile = createProfile(user);
        ProfileUpdateRequest request = new ProfileUpdateRequest(
            "new-name",
            "ETC",
            "1999-01-01",
            createLocationDto(),
            4
        );
        MockMultipartFile profileImage = new MockMultipartFile(
            "profileImage",
            "profile.png",
            "image/png",
            "image-bytes".getBytes()
        );

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user)).thenReturn(Optional.of(profile));
        when(locationRepository.findByProfile(profile))
            .thenReturn(Optional.empty(), Optional.of(Location.from(emptyLocationDto(), profile)));
        when(s3StorageService.putObject(any(String.class), any(byte[].class))).thenReturn("s3-key");
        when(s3StorageService.getPresignedUrl("s3-key")).thenReturn("https://example.com/s3-key");
        when(profileRepository.save(profile)).thenReturn(profile);

        ProfileDto result = profileService.updateProfile(user.getId(), user.getId(), request, profileImage);

        verify(locationRepository).save(any(Location.class));
        verify(s3StorageService).putObject(any(String.class), any(byte[].class));
        assertEquals("new-name", result.name());
        assertEquals("ETC", result.gender());
        assertEquals("1999-01-01", result.birthDate());
        assertEquals("https://example.com/s3-key", result.profileImageUrl());
        assertEquals(List.of(), result.location().locationNames());
    }

    @Test
    void updateProfileUpdatesExistingLocationWhenImageMissing() {
        User user = createUser();
        Profile profile = createProfile(user);
        ProfileUpdateRequest request = new ProfileUpdateRequest(
            "new-name",
            "MALE",
            "2001-02-03",
            createLocationDto(),
            1
        );
        Location existingLocation = Location.from(
            new LocationDto(37.0, 127.0, 1, 2, List.of("old")),
            profile
        );

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user)).thenReturn(Optional.of(profile));
        when(locationRepository.findByProfile(profile))
            .thenReturn(Optional.of(existingLocation), Optional.of(existingLocation));
        when(profileRepository.save(profile)).thenReturn(profile);

        ProfileDto result = profileService.updateProfile(user.getId(), user.getId(), request, null);

        verify(locationRepository).save(existingLocation);
        assertEquals("new-name", result.name());
        assertEquals("MALE", result.gender());
        assertEquals("2001-02-03", result.birthDate());
        assertEquals(List.of("seoul", "gangnam"), result.location().locationNames());
    }

    @Test
    void updateProfileThrowsWhenImageReadFails() throws IOException {
        User user = createUser();
        Profile profile = createProfile(user);
        ProfileUpdateRequest request = new ProfileUpdateRequest("n", "ETC", "1999-01-01", null, 3);
        MockMultipartFile profileImage = new MockMultipartFile("profileImage", "p.png", "image/png", "x".getBytes()) {
            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("read failed");
            }
        };

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileRepository.findByUserId(user)).thenReturn(Optional.of(profile));

        assertThrows(
            S3UploadFailedException.class,
            () -> profileService.updateProfile(user.getId(), user.getId(), request, profileImage)
        );
    }

    @Test
    @DisplayName("인증 사용자는 다른 사용자의 프로필을 변경할 수 없다")
    void updateProfileRejectsAnotherUser() {
        UUID currentUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        ProfileUpdateRequest request = easyRandom.nextObject(ProfileUpdateRequest.class);

        assertThrows(
            ForbiddenException.class,
            () -> profileService.updateProfile(currentUserId, targetUserId, request, null)
        );
        verify(userRepository, never()).findById(any());
    }

    private User createUser() {
        return new User(
            UUID.randomUUID(),
            "user@example.com",
            "name",
            Provider.LOCAL,
            Role.USER,
            false,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 1, 0, 0),
            null,
            "password",
            null,
            null
        );
    }

    private Profile createProfile(User user) {
        return new Profile(
            UUID.randomUUID(),
            "name",
            "2000-01-01",
            3,
            null,
            Gender.ETC,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 1, 0, 0),
            0L,
            0L,
            user
        );
    }

    private LocationDto createLocationDto() {
        return new LocationDto(37.5665, 126.9780, 60, 127, List.of("seoul", "gangnam"));
    }

    private LocationDto emptyLocationDto() {
        return new LocationDto(0.0, 0.0, 0, 0, List.of());
    }
}
