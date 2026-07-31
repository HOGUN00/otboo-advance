package codeit.sb06.otboo.notification.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRepositoryTest {

    private SseEmitterRepository sseEmitterRepository;

    private UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sseEmitterRepository = new SseEmitterRepository();
    }

    @Test
    @DisplayName("SseEmitter를 저장하고 조회할 수 있다.")
    void saveSseEmitterTest() {
        // given
        SseEmitter emitter = new SseEmitter();

        // when
        sseEmitterRepository.save(userId, emitter);

        // then
        assertThat(sseEmitterRepository.findById(userId)).isEqualTo(emitter);
    }

    @Test
    @DisplayName("SseEmitter를 삭제할 수 있다.")
    void deleteSseEmitterTest() {
        // given
        SseEmitter emitter = new SseEmitter();
        sseEmitterRepository.save(userId, emitter);

        // when
        sseEmitterRepository.deleteById(userId);

        // then
        assertThat(sseEmitterRepository.findById(userId)).isNull();
    }

    @Test
    @DisplayName("현재 저장된 SseEmitter와 일치할 때만 삭제할 수 있다.")
    void deleteIfMatchesTest() {
        // given
        SseEmitter emitter = new SseEmitter();
        sseEmitterRepository.save(userId, emitter);

        // when
        boolean deleted = sseEmitterRepository.deleteIfMatches(userId, emitter);

        // then
        assertThat(deleted).isTrue();
        assertThat(sseEmitterRepository.findById(userId)).isNull();
    }

    @Test
    @DisplayName("이전 SseEmitter의 콜백은 새 SseEmitter를 삭제하지 않는다.")
    void deleteIfMatchesDoesNotDeleteReplacedEmitterTest() {
        // given
        SseEmitter oldEmitter = new SseEmitter();
        SseEmitter newEmitter = new SseEmitter();
        sseEmitterRepository.save(userId, oldEmitter);
        sseEmitterRepository.save(userId, newEmitter);

        // when
        boolean deleted = sseEmitterRepository.deleteIfMatches(userId, oldEmitter);

        // then
        assertThat(deleted).isFalse();
        assertThat(sseEmitterRepository.findById(userId)).isSameAs(newEmitter);
    }
}
