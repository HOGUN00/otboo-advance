package codeit.sb06.otboo.security;

import codeit.sb06.otboo.user.dto.UserDto;
import codeit.sb06.otboo.user.entity.Role;
import codeit.sb06.otboo.user.entity.User;
import codeit.sb06.otboo.util.EasyRandomUtil;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.springframework.test.util.ReflectionTestUtils;

public final class SecurityTestFixtures {

  private static final EasyRandom EASY_RANDOM = EasyRandomUtil.getRandom();

  private SecurityTestFixtures() {
  }

  public static UserDto userDto(UUID userId, Role role) {
    UserDto randomUser = EASY_RANDOM.nextObject(UserDto.class);
    return new UserDto(
        userId,
        randomUser.email(),
        randomUser.createdAt(),
        role.name(),
        randomUser.isLocked()
    );
  }

  public static User user(UUID userId, Role role) {
    User randomUser = EASY_RANDOM.nextObject(User.class);
    ReflectionTestUtils.setField(randomUser, "id", userId);
    ReflectionTestUtils.setField(randomUser, "role", role);
    return randomUser;
  }
}
