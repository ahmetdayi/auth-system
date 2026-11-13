package tr.gov.kurum.identity.infrastructure.adapter.in.scheduling;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param enabled           turned off in tests, which call the use case directly
 * @param cron              Spring cron expression (seconds first)
 * @param batchSize         rows deleted per transaction
 * @param maxBatchesPerRun  upper bound per run, so one run never monopolises the database
 */
@Validated
@ConfigurationProperties(prefix = "app.session-purge")
public record SessionPurgeProperties(
        boolean enabled,
        @NotBlank String cron,
        @Min(1) @Max(10_000) int batchSize,
        @Min(1) int maxBatchesPerRun) {
}
