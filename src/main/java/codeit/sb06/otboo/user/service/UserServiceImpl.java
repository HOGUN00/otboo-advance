package codeit.sb06.otboo.user.service;

import codeit.sb06.otboo.exception.auth.ForbiddenException;
import codeit.sb06.otboo.exception.user.MailSendException;
import codeit.sb06.otboo.exception.user.UserAlreadyExistException;
import codeit.sb06.otboo.exception.user.UserNotFoundException;
import codeit.sb06.otboo.notification.publisher.NotificationEventPublisher;
import codeit.sb06.otboo.profile.service.ProfileServiceImpl;
import codeit.sb06.otboo.security.jwt.JwtRegistry;
import codeit.sb06.otboo.user.dto.UserDto;
import codeit.sb06.otboo.user.dto.request.ChangePasswordRequest;
import codeit.sb06.otboo.user.dto.request.UserCreateRequest;
import codeit.sb06.otboo.user.dto.request.UserSliceRequest;
import codeit.sb06.otboo.user.dto.response.UserDtoCursorResponse;
import codeit.sb06.otboo.user.entity.Role;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.user.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Slice;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileServiceImpl profileService;
    private final JavaMailSender mailSender;
    private final JwtRegistry jwtRegistry;
    private final NotificationEventPublisher notificationEventPublisher;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${otboo.mail.from}")
    private String from;

    @Transactional
    public UserDto create(UserCreateRequest userCreateRequest) {
        log.debug("Create user start: {}", userCreateRequest);

        if(userRepository.findByEmail(userCreateRequest.email()).isPresent()){
            throw new UserAlreadyExistException();
        }

        User user = User.from(userCreateRequest);
        user.setEncryptPassword(passwordEncoder, userCreateRequest.password());

        User savedUser = userRepository.save(user);
        profileService.create(savedUser);
        return UserDto.from(savedUser);
    }

    public UserDtoCursorResponse getUsersCursor(UserSliceRequest userSliceRequest){
        log.debug("Get users cursor start: {}", userSliceRequest);

        Slice<User> response = userRepository.findUsersBySlice(userSliceRequest);
        return UserDtoCursorResponse.from(response, userSliceRequest);
    }

    @Transactional
    public UserDto changeUserRole(UUID userId, String userRole){
        log.debug("Change user role start: {}, {}", userId, userRole);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        user.changeRole(Role.valueOf(userRole));
        User updatedUser = userRepository.save(user);
        notificationEventPublisher.publishRoleUpdatedEvent(userId, Role.valueOf(userRole));
        return UserDto.from(updatedUser);
    }

    @Transactional
    public UserDto changeLockStatus(UUID userId, boolean lockStatus){
        log.debug("Change user lock status start: {}, {}", userId, lockStatus);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        user.changeLockStatus(lockStatus);
        User updatedUser = userRepository.save(user);
        if(lockStatus){
            jwtRegistry.invalidateJwtInformationByUserId(userId);
        }
        return UserDto.from(updatedUser);
    }

    @Transactional
    public void send(String toEmail) {
        User user = userRepository.findByEmail(toEmail).orElseThrow(UserNotFoundException::new);
        try{
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("[Otboo] 임시 비밀번호 안내");

            LocalDateTime expireTime = LocalDateTime.now().plusMinutes(3);
            String tempPassword = generateTemporaryPassword();
            user.updateTempPassword(passwordEncoder.encode(tempPassword), expireTime);
            String expireText = expireTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String body = """
                안녕하세요 Otboo입니다.
                
                임시 비밀번호: %s
                만료시간: %s
                
                로그인 후 반드시 비밀번호를 변경해주세요
                """.formatted(tempPassword, expireText);

            helper.setText(body);
            mailSender.send(message);
        } catch (Exception e){
            throw new MailSendException();
        }
    }

    private String generateTemporaryPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        StringBuilder tempPassword = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            int index = (int) (SECURE_RANDOM.nextInt(chars.length()));
            tempPassword.append(chars.charAt(index));
        }
        return tempPassword.toString();
    }

    @Transactional
    public void changePassword(UUID currentUserId, UUID userId, ChangePasswordRequest changePasswordRequest) {
        if (!currentUserId.equals(userId)) {
            throw new ForbiddenException();
        }
        log.debug("Change password requested for userId: {}", userId);
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.setEncryptPassword(passwordEncoder, changePasswordRequest.password());
    }
}
