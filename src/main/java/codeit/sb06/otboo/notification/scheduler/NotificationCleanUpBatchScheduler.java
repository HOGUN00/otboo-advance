package codeit.sb06.otboo.notification.scheduler;

import codeit.sb06.otboo.exception.notification.NotificationBatchException;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
public class NotificationCleanUpBatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job deleteOldNotificationsJob;

    @Scheduled(cron = "0 0 0 * * ?")
    @SchedulerLock(name = "NotificationCleanUpBatchSchedulerLock", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10M")
    public void runDailyJob() {

        try {
            LocalDateTime cutoffDate = LocalDate.now()
                    .minusDays(30)
                    .atStartOfDay();

            JobParameters params = new JobParametersBuilder()
                    .addLocalDateTime("cutoffDate", cutoffDate)
                    .toJobParameters();
            jobLauncher.run(deleteOldNotificationsJob, params);
        } catch (Exception e) {
            throw new NotificationBatchException(e);
        }
    }
}
