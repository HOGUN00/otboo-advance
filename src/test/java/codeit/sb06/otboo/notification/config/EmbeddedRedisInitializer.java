package codeit.sb06.otboo.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import redis.embedded.RedisServer;

/**
 * Spring 컨텍스트가 만들어지기 전에 Embedded Redis를 실행하기 위한 초기화 클래스다.
 * 애플리케이션 시작 시 Redis에 접근하는 기능을 포함한 통합 테스트에서 사용한다.
 */
@Slf4j
public class EmbeddedRedisInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        int redisPort = Integer.parseInt(
                context.getEnvironment().getRequiredProperty("spring.data.redis.port"));
        RedisServer redisServer;

        try {
            redisServer = new RedisServer(redisPort);
            redisServer.start();
        } catch (Exception e) {
            throw new IllegalStateException("Embedded Redis를 시작할 수 없습니다.", e);
        }

        DefaultListableBeanFactory beanFactory =
                (DefaultListableBeanFactory) context.getBeanFactory();
        String redisServerBeanName = "integrationTestRedisServer";
        beanFactory.registerSingleton(redisServerBeanName, redisServer);
        beanFactory.registerDependentBean(redisServerBeanName, "redisStreamManager");
        beanFactory.registerDependentBean(redisServerBeanName, "container");
        beanFactory.registerDependentBean(redisServerBeanName, "redisConnectionFactory");
        beanFactory.registerDisposableBean(redisServerBeanName, () -> {
            if (redisServer.isActive()) {
                try {
                    redisServer.stop();
                } catch (Exception e) {
                    log.warn("Embedded Redis 종료 중 오류가 발생했습니다.", e);
                }
            }
        });
    }
}
