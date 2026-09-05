package codeit.sb06.otboo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.sb06.otboo.config.QueryDslConfig;
import codeit.sb06.otboo.notification.mapper.NotificationMapper;
import codeit.sb06.otboo.notification.repository.NotificationRepository;
import codeit.sb06.otboo.notification.service.impl.NotificationServiceImpl;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({
        NotificationServiceImpl.class,
        NotificationMapper.class,
        QueryDslConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationTransactionIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("알림 저장은 호출한 트랜잭션이 롤백되면 함께 롤백된다")
    void createJoinsCallerTransaction() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            notificationService.createDirectMessageInCurrentTransaction(
                    UUID.randomUUID(),
                    "sender",
                    "content");
            status.setRollbackOnly();
        });

        assertThat(notificationRepository.count()).isZero();
    }
}
