package tr.gov.kurum.identity.infrastructure.adapter.in.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tr.gov.kurum.identity.application.port.in.PurgeExpiredSessionsUseCase;

/**
 * Deletes expired sessions in small batches, each in its own short transaction (via the use case).
 *
 * <p>Safe without a distributed lock: the delete is idempotent and uses {@code SKIP LOCKED}, so several
 * instances running at once simply share the work. A failed run is retried by the next one.</p>
 */
@Component
@EnableScheduling
@EnableConfigurationProperties(SessionPurgeProperties.class)
@ConditionalOnBooleanProperty(name = "app.session-purge.enabled")
class ExpiredSessionPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredSessionPurgeJob.class);

    private final PurgeExpiredSessionsUseCase purgeUseCase;
    private final SessionPurgeProperties properties;

    ExpiredSessionPurgeJob(PurgeExpiredSessionsUseCase purgeUseCase, SessionPurgeProperties properties) {
        this.purgeUseCase = purgeUseCase;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.session-purge.cron}")
    void run() {
        int total = 0;
        for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
            int deleted = purgeUseCase.purgeExpiredSessions(properties.batchSize());
            total += deleted;
            if (deleted < properties.batchSize()) {
                break;
            }
        }
        if (total > 0) {
            log.info("Expired sessions purged: count={}", total);
        }
    }
}
