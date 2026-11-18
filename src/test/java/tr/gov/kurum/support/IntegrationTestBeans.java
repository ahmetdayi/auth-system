package tr.gov.kurum.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
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

    @Bean
    CountingStatementInspector countingStatementInspector() {
        return new CountingStatementInspector();
    }

    /** Wires the inspector into Hibernate so N+1 guard tests can count SQL. */
    @Bean
    HibernatePropertiesCustomizer countingStatementInspectorCustomizer(CountingStatementInspector inspector) {
        return (Map<String, Object> properties) -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, inspector);
    }
}
