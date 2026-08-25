package codeit.sb06.otboo.notification.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationCleanUpJobIntegrationTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job deleteOldNotificationsJob;

    @Test
    @DisplayName("오래된 알림 삭제 Job 정상 실행 통합 테스트")
    void deleteOldNotificationsJob_Success() throws Exception {
        // given
        JobParameters params = new JobParametersBuilder()
                .addLocalDateTime("cutoffDate", LocalDate.now().minusDays(30).atStartOfDay())
                .toJobParameters();

        // when
        JobExecution execution = jobLauncher.run(deleteOldNotificationsJob, params);

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
}
