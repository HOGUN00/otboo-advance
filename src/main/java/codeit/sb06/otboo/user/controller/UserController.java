package codeit.sb06.otboo.user.controller;

import codeit.sb06.otboo.profile.dto.ProfileDto;
import codeit.sb06.otboo.profile.dto.ProfileUpdateRequest;
import codeit.sb06.otboo.profile.service.ProfileServiceImpl;
import codeit.sb06.otboo.security.resolver.CurrentUserId;
import codeit.sb06.otboo.security.resolver.RequireRole;
import codeit.sb06.otboo.user.dto.UserDto;
import codeit.sb06.otboo.user.dto.request.ChangePasswordRequest;
import codeit.sb06.otboo.user.dto.request.UserCreateRequest;
import codeit.sb06.otboo.user.dto.request.UserLockUpdateRequest;
import codeit.sb06.otboo.user.dto.request.UserRoleUpdateRequest;
import codeit.sb06.otboo.user.dto.request.UserSliceRequest;
import codeit.sb06.otboo.user.dto.response.UserDtoCursorResponse;
import codeit.sb06.otboo.user.entity.Role;
import codeit.sb06.otboo.user.service.UserServiceImpl;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserServiceImpl userServiceImpl;
    private final ProfileServiceImpl profileServiceImpl;

    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody UserCreateRequest userCreateRequest){
        log.info("Create user requested: {}", userCreateRequest);
        UserDto createdUser = userServiceImpl.create(userCreateRequest);
        return ResponseEntity.ok(createdUser);
    }

    @GetMapping
    @RequireRole(Role.ADMIN)
    public ResponseEntity<UserDtoCursorResponse> getUser(@ModelAttribute UserSliceRequest userSliceRequest){
        log.info("Get users requested: {}", userSliceRequest);
        UserDtoCursorResponse userDtoCursorResponse = userServiceImpl.getUsersCursor(userSliceRequest);
        return ResponseEntity.ok(userDtoCursorResponse);
    }

    @PatchMapping("/{userId}/role")
    @RequireRole(Role.ADMIN)
    public ResponseEntity<UserDto> updateRole(@PathVariable String userId, @RequestBody UserRoleUpdateRequest role){
        log.info("Update role requested: {}", role);
        UserDto updatedUser = userServiceImpl.changeUserRole(UUID.fromString(userId), role.role());
        return ResponseEntity.ok(updatedUser);
    }

    @PatchMapping("/{userId}/lock")
    @RequireRole(Role.ADMIN)
    public ResponseEntity<UserDto> updateLock(@PathVariable String userId, @RequestBody UserLockUpdateRequest lockUpdateRequest){
        log.info("Update lock requested: {}", lockUpdateRequest);
        UserDto updatedUser = userServiceImpl.changeLockStatus(UUID.fromString(userId), lockUpdateRequest.locked());
        return ResponseEntity.ok(updatedUser);
    }

    @GetMapping("/{userId}/profiles")
    public ResponseEntity<ProfileDto> getProfile(@PathVariable UUID userId){
        log.info("Get profile requested for userId: {}", userId);
        ProfileDto profileDto = profileServiceImpl.getProfileByUserId(userId);
        return ResponseEntity.ok(profileDto);
    }

    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> changePassword(
            @CurrentUserId UUID currentUserId,
            @PathVariable UUID userId,
            @RequestBody ChangePasswordRequest changePasswordRequest){
        log.info("Change password requested for userId: {}", userId);
        userServiceImpl.changePassword(currentUserId, userId, changePasswordRequest);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(value = "/{userId}/profiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileDto> updateProfile(
            @CurrentUserId UUID currentUserId,
            @PathVariable UUID userId,
        @RequestPart(value = "request") ProfileUpdateRequest profileUpdateRequest,
        @RequestPart(value = "image", required = false) MultipartFile profileImage){

        log.info("Update profile requested for userId: {}", userId);
        ProfileDto updatedProfile = profileServiceImpl.updateProfile(currentUserId, userId, profileUpdateRequest, profileImage);
        return ResponseEntity.ok(updatedProfile);

    }

}
