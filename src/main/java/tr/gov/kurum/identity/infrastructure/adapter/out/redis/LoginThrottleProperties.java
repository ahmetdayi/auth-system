package tr.gov.kurum.identity.infrastructure.adapter.out.redis;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param maxFailuresPerAccount failed logins allowed for one (e-mail, IP) pair within the window
 * @param maxFailuresPerIp      failed logins allowed from one IP across all e-mails within the window
 * @param window                counting window; it starts with the first failure
 */
@Validated
@ConfigurationProperties(prefix = "app.login-throttle")
public record LoginThrottleProperties(
        @Min(1) int maxFailuresPerAccount,
        @Min(1) int maxFailuresPerIp,
        @NotNull Duration window) {
}
