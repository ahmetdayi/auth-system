package tr.gov.kurum.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** Beans added to the application context of the integration tests. */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestBeans {

    /** Replaces the system clock everywhere (tokens, sessions, auditing), so tests move time instead of waiting. */
    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @Bean
    TestData testData(JdbcTemplate jdbc, MutableClock clock) {
        return new TestData(jdbc, clock);
    }
}
