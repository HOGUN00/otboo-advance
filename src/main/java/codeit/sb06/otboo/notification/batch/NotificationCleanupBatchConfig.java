package codeit.sb06.otboo.notification.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Configuration
public class NotificationCleanupBatchConfig {

    @Bean
    public Job deleteOldNotificationsJob(
            JobRepository jobRepository,
            Step deleteOldNotificationsStep
    ) {
        return new JobBuilder("deleteOldNotificationsJob", jobRepository)
                .start(deleteOldNotificationsStep)
                .build();
    }

    @Bean
    public Step deleteOldNotificationsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcPagingItemReader<UUID> expiredNotificationReader,
            JdbcBatchItemWriter<UUID> expiredNotificationWriter
    ) {
        return new StepBuilder("deleteOldNotificationsStep", jobRepository)
                .<UUID, UUID>chunk(100, transactionManager)
                .reader(expiredNotificationReader)
                .writer(expiredNotificationWriter)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .backOffPolicy(new FixedBackOffPolicy())    // 1초 간격으로 재시도
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<UUID> expiredNotificationReader(
            DataSource dataSource,
            @Value("#{jobParameters['cutoffDate']}") LocalDateTime cutoffDate
    ) {
        Map<String, Order> sortKeys = new LinkedHashMap<>();
        sortKeys.put("created_at", Order.ASCENDING);
        sortKeys.put("id", Order.ASCENDING);

        return new JdbcPagingItemReaderBuilder<UUID>()
                .name("expiredNotificationReader")
                .selectClause("SELECT id, created_at")
                .fromClause("FROM notifications")
                .whereClause("WHERE created_at < :date")
                .parameterValues(Map.of("date", cutoffDate))
                .dataSource(dataSource)
                .rowMapper((rs, rowNum) -> UUID.fromString(rs.getString("id")))
                .sortKeys(sortKeys)
                .pageSize(100)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<UUID> expiredNotificationWriter(
            DataSource dataSource
    ) {
        return new JdbcBatchItemWriterBuilder<UUID>()
                .dataSource(dataSource)
                .sql("DELETE FROM notifications WHERE id = ?")
                .itemPreparedStatementSetter(
                        (id, statement) -> statement.setObject(1, id)
                )
                .build();
    }
}
