package tr.gov.kurum.common.infrastructure.adapter.in.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.outbox.OutboxRelay;

/** Runs the outbox relay continuously and cleans up delivered events once a day. */
@Component
@EnableScheduling
@ConditionalOnBooleanProperty(name = "app.outbox.relay-enabled")
class OutboxRelayJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);

    private final OutboxRelay relay;

    OutboxRelayJob(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms}")
    void deliver() {
        int delivered = relay.deliverDue();
        if (delivered > 0) {
            log.debug("Outbox events delivered: {}", delivered);
        }
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron}")
    void cleanUp() {
        int deleted = relay.deleteDelivered();
        if (deleted > 0) {
            log.info("Delivered outbox events deleted: {}", deleted);
        }
    }
}
