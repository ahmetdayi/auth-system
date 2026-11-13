package tr.gov.kurum.common.infrastructure.adapter.out.persistence.outbox;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param relayEnabled    turned off in tests, which deliver explicitly
 * @param relayIntervalMs pause between two relay runs
 * @param batchSize       events delivered per run at most
 * @param maxAttempts     failed deliveries after which an event is left for manual inspection
 * @param retention       how long delivered events are kept
 * @param cleanupCron     when delivered events older than the retention are deleted
 */
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        boolean relayEnabled,
        @Min(50) long relayIntervalMs,
        @Min(1) @Max(1000) int batchSize,
        @Min(1) @Max(50) int maxAttempts,
        @NotNull Duration retention,
        @NotBlank String cleanupCron) {
}
