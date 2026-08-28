package codeit.sb06.otboo.profile.service;

import codeit.sb06.otboo.exception.auth.ForbiddenException;
import codeit.sb06.otboo.exception.profile.ProfileNotFoundException;
import codeit.sb06.otboo.exception.profile.S3UploadFailedException;
import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.profile.dto.LocationDto;
import codeit.sb06.otboo.profile.dto.ProfileDto;
import codeit.sb06.otboo.profile.dto.ProfileUpdateRequest;
import codeit.sb06.otboo.profile.entity.Location;
import codeit.sb06.otboo.profile.entity.Profile;
import codeit.sb06.otboo.profile.repository.LocationRepository;
import codeit.sb06.otboo.profile.repository.ProfileRepository;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileServiceImpl {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final S3StorageService s3StorageService;
    private final LocationRepository locationRepository;

    @Transactional
    public ProfileDto create(User user){
        log.debug("create profile start: {}", user.getId());
        if(userRepository.findByEmail(user.getEmail()).isEmpty()){
            throw new UserNotFoundException();
        }

        Profile profile = Profile.from(user);
        Profile savedProfile = profileRepository.save(profile);
        return ProfileDto.from(savedProfile, findLocationNames(savedProfile), s3StorageService);
    }

    public ProfileDto getProfileByUserId(UUID userId){
        log.debug("get profile by userId start: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        Profile profile = profileRepository.findByUserId(user)
                .orElseThrow(ProfileNotFoundException::new);

        return ProfileDto.from(profile, findLocationNames(profile), s3StorageService);
    }

    @Transactional
    public ProfileDto updateProfile(UUID currentUserId, UUID userId, ProfileUpdateRequest profileUpdateRequest, @Nullable MultipartFile profileImage){

        if (!currentUserId.equals(userId)) {
            throw new ForbiddenException();
        }

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Profile profile = profileRepository.findByUserId(user).orElseThrow(ProfileNotFoundException::new);
        profile.updateProfile(profileUpdateRequest);

        if(profileUpdateRequest.location() != null) {
            Location location = locationRepository.findByProfile(profile).orElse(null);
            if(location == null) {
                Location newLocation = Location.from(profileUpdateRequest.location(), profile);
                locationRepository.save(newLocation);
            } else {
                location.updateLocation(profileUpdateRequest.location());
                locationRepository.save(location);
            }
        }

        if (profileImage != null && !profileImage.isEmpty()) {
            UUID fileId = UUID.randomUUID();
            try {
                profile.changeProfileImage(
                    s3StorageService.putObject(String.valueOf(fileId), profileImage.getBytes())
                );
            } catch (IOException e) {
                throw new S3UploadFailedException(e);
            }
        }

        Profile updatedProfile = profileRepository.save(profile);
        return ProfileDto.from(updatedProfile, findLocationNames(updatedProfile), s3StorageService);
    }

    private LocationDto findLocationNames(Profile profile) {
        return LocationDto.from(locationRepository.findByProfile(profile).orElse(null));
    }
}
