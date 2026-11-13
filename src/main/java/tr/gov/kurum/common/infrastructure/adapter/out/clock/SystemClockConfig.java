package tr.gov.kurum.common.infrastructure.adapter.out.clock;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single source of time. Code never calls {@code Instant.now()} directly, so tests can move the clock. */
@Configuration(proxyBeanMethods = false)
public class SystemClockConfig {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
