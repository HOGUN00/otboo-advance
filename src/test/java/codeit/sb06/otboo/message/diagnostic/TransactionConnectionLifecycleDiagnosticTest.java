package codeit.sb06.otboo.message.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Disabled("Connection 생명주기 확인이 필요할 때만 수동으로 활성화한다")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TransactionConnectionLifecycleDiagnosticTest.TestConfig.class)
class TransactionConnectionLifecycleDiagnosticTest {

    private final OuterTransactionService outerTransactionService;
    private final SameTransactionService sameTransactionService;
    private final ConnectionTrace connectionTrace;

    @Autowired
    TransactionConnectionLifecycleDiagnosticTest(
            OuterTransactionService outerTransactionService,
            SameTransactionService sameTransactionService,
            ConnectionTrace connectionTrace) {
        this.outerTransactionService = outerTransactionService;
        this.sameTransactionService = sameTransactionService;
        this.connectionTrace = connectionTrace;
    }

    @BeforeEach
    void resetTrace() {
        connectionTrace.reset();
    }

    @Test
    @DisplayName("AFTER_COMMIT의 REQUIRES_NEW는 외부 Connection 반환 전에 새 Connection을 획득한다")
    void requiresNewAcquiresAnotherConnectionBeforeOuterConnectionIsReturned() {
        outerTransactionService.execute();

        List<String> events = connectionTrace.events();
        events.forEach(System.out::println);

        assertThat(events).containsSubsequence(
                "connection-1 acquired",
                "transaction afterCommit",
                "transaction afterCompletion: COMMITTED",
                "AFTER_COMMIT listener entered",
                "connection-2 acquired",
                "connection-2 released",
                "connection-1 released");
    }

    @Test
    @DisplayName("같은 트랜잭션의 DB 작업과 AFTER_COMMIT Redis 작업은 Connection 하나를 사용한다")
    void requiredDbWorkAndAfterCommitRedisWorkUseOneConnection() {
        sameTransactionService.execute();

        List<String> events = connectionTrace.events();
        events.forEach(System.out::println);

        assertThat(events).containsSubsequence(
                "connection-1 acquired",
                "notification DB work entered",
                "transaction afterCommit",
                "transaction afterCompletion: COMMITTED",
                "AFTER_COMMIT Redis listener entered",
                "connection-1 released");
        assertThat(events).doesNotContain("connection-2 acquired");
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        ConnectionTrace connectionTrace() {
            return new ConnectionTrace();
        }

        @Bean
        DataSource dataSource(ConnectionTrace connectionTrace) {
            DriverManagerDataSource target = new DriverManagerDataSource(
                    "jdbc:h2:mem:connection-lifecycle;DB_CLOSE_DELAY=-1",
                    "sa",
                    "");
            return new TracingDataSource(target, connectionTrace);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        OuterTransactionService outerTransactionService(
                JdbcTemplate jdbcTemplate,
                ApplicationEventPublisher eventPublisher,
                ConnectionTrace connectionTrace) {
            return new OuterTransactionService(jdbcTemplate, eventPublisher, connectionTrace);
        }

        @Bean
        AfterCommitListener afterCommitListener(
                RequiresNewService requiresNewService,
                ConnectionTrace connectionTrace) {
            return new AfterCommitListener(requiresNewService, connectionTrace);
        }

        @Bean
        RequiresNewService requiresNewService(JdbcTemplate jdbcTemplate) {
            return new RequiresNewService(jdbcTemplate);
        }

        @Bean
        SameTransactionService sameTransactionService(
                RequiredNotificationService requiredNotificationService,
                ApplicationEventPublisher eventPublisher,
                ConnectionTrace connectionTrace) {
            return new SameTransactionService(
                    requiredNotificationService,
                    eventPublisher,
                    connectionTrace);
        }

        @Bean
        RequiredNotificationService requiredNotificationService(
                JdbcTemplate jdbcTemplate,
                ConnectionTrace connectionTrace) {
            return new RequiredNotificationService(jdbcTemplate, connectionTrace);
        }

        @Bean
        AfterCommitRedisListener afterCommitRedisListener(ConnectionTrace connectionTrace) {
            return new AfterCommitRedisListener(connectionTrace);
        }
    }

    static class OuterTransactionService {

        private final JdbcTemplate jdbcTemplate;
        private final ApplicationEventPublisher eventPublisher;
        private final ConnectionTrace connectionTrace;

        OuterTransactionService(
                JdbcTemplate jdbcTemplate,
                ApplicationEventPublisher eventPublisher,
                ConnectionTrace connectionTrace) {
            this.jdbcTemplate = jdbcTemplate;
            this.eventPublisher = eventPublisher;
            this.connectionTrace = connectionTrace;
        }

        @Transactional
        public void execute() {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    connectionTrace.record("transaction afterCommit");
                }

                @Override
                public void afterCompletion(int status) {
                    String completion = status == STATUS_COMMITTED ? "COMMITTED" : String.valueOf(status);
                    connectionTrace.record("transaction afterCompletion: " + completion);
                }
            });
            eventPublisher.publishEvent(new WorkCreatedEvent());
        }
    }

    static class AfterCommitListener {

        private final RequiresNewService requiresNewService;
        private final ConnectionTrace connectionTrace;

        AfterCommitListener(RequiresNewService requiresNewService, ConnectionTrace connectionTrace) {
            this.requiresNewService = requiresNewService;
            this.connectionTrace = connectionTrace;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void handle(WorkCreatedEvent event) {
            connectionTrace.record("AFTER_COMMIT listener entered");
            requiresNewService.execute();
        }
    }

    static class RequiresNewService {

        private final JdbcTemplate jdbcTemplate;

        RequiresNewService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void execute() {
            jdbcTemplate.queryForObject("select 1", Integer.class);
        }
    }

    static class SameTransactionService {

        private final RequiredNotificationService requiredNotificationService;
        private final ApplicationEventPublisher eventPublisher;
        private final ConnectionTrace connectionTrace;

        SameTransactionService(
                RequiredNotificationService requiredNotificationService,
                ApplicationEventPublisher eventPublisher,
                ConnectionTrace connectionTrace) {
            this.requiredNotificationService = requiredNotificationService;
            this.eventPublisher = eventPublisher;
            this.connectionTrace = connectionTrace;
        }

        @Transactional
        public void execute() {
            requiredNotificationService.execute();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    connectionTrace.record("transaction afterCommit");
                }

                @Override
                public void afterCompletion(int status) {
                    String completion = status == STATUS_COMMITTED ? "COMMITTED" : String.valueOf(status);
                    connectionTrace.record("transaction afterCompletion: " + completion);
                }
            });
            eventPublisher.publishEvent(new NotificationStoredEvent());
        }
    }

    static class RequiredNotificationService {

        private final JdbcTemplate jdbcTemplate;
        private final ConnectionTrace connectionTrace;

        RequiredNotificationService(JdbcTemplate jdbcTemplate, ConnectionTrace connectionTrace) {
            this.jdbcTemplate = jdbcTemplate;
            this.connectionTrace = connectionTrace;
        }

        @Transactional
        public void execute() {
            connectionTrace.record("notification DB work entered");
            jdbcTemplate.queryForObject("select 1", Integer.class);
        }
    }

    static class AfterCommitRedisListener {

        private final ConnectionTrace connectionTrace;

        AfterCommitRedisListener(ConnectionTrace connectionTrace) {
            this.connectionTrace = connectionTrace;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void handle(NotificationStoredEvent event) {
            connectionTrace.record("AFTER_COMMIT Redis listener entered");
        }
    }

    static final class WorkCreatedEvent {
    }

    static final class NotificationStoredEvent {
    }

    static class ConnectionTrace {

        private final AtomicInteger sequence = new AtomicInteger();
        private final List<String> events = new CopyOnWriteArrayList<>();

        int nextConnectionId() {
            return sequence.incrementAndGet();
        }

        void record(String event) {
            events.add(event);
        }

        List<String> events() {
            return List.copyOf(events);
        }

        void reset() {
            sequence.set(0);
            events.clear();
        }
    }

    static class TracingDataSource implements DataSource {

        private final DataSource delegate;
        private final ConnectionTrace connectionTrace;

        TracingDataSource(DataSource delegate, ConnectionTrace connectionTrace) {
            this.delegate = delegate;
            this.connectionTrace = connectionTrace;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return trace(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return trace(delegate.getConnection(username, password));
        }

        private Connection trace(Connection target) {
            int id = connectionTrace.nextConnectionId();
            connectionTrace.record("connection-" + id + " acquired");

            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("close")) {
                            try {
                                return method.invoke(target, args);
                            } catch (InvocationTargetException exception) {
                                throw exception.getTargetException();
                            } finally {
                                connectionTrace.record("connection-" + id + " released");
                            }
                        }

                        try {
                            return method.invoke(target, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getTargetException();
                        }
                    });
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }
    }
}
