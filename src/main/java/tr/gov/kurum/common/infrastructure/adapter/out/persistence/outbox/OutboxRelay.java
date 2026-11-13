package tr.gov.kurum.common.infrastructure.adapter.out.persistence.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tr.gov.kurum.common.domain.IntegrationEvent;
import tr.gov.kurum.common.infrastructure.adapter.in.messaging.DeliveredEvent;

/**
 * Delivers outbox events to their consumers ({@link DeliveredEvent} listeners), one event per transaction:
 * the consumers' changes and the "published" mark are committed together. If a consumer fails, both roll
 * back and the event is retried later with a growing pause.
 *
 * <p>Consumers must still be idempotent: delivery is "at least once" in general (a consumer with a side
 * effect outside this database, or a later relay through a message broker, can see an event twice).</p>
 */
@Component
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
    private static final int CLEANUP_BATCH_SIZE = 500;

    private final OutboxEventJpaRepository repository;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    OutboxRelay(OutboxEventJpaRepository repository, JsonMapper jsonMapper, ApplicationEventPublisher publisher,
                TransactionTemplate transactionTemplate, OutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.publisher = publisher;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return the number of events delivered in this run */
    public int deliverDue() {
        int delivered = 0;
        for (int i = 0; i < properties.batchSize(); i++) {
            Outcome outcome = deliverNext();
            if (outcome == Outcome.NOTHING_DUE) {
                break;
            }
            if (outcome == Outcome.DELIVERED) {
                delivered++;
            }
        }
        return delivered;
    }

    /** Deletes delivered events older than the retention, in batches. */
    public int deleteDelivered() {
        Instant before = clock.instant().minus(properties.retention());
        int total = 0;
        int deleted;
        do {
            Integer batch = transactionTemplate.execute(status ->
                repository.deletePublishedBefore(before, CLEANUP_BATCH_SIZE));
            deleted = batch == null ? 0 : batch;
            total += deleted;
        } while (deleted == CLEANUP_BATCH_SIZE);
        return total;
    }

    private Outcome deliverNext() {
        Instant now = clock.instant();
        AtomicReference<Long> current = new AtomicReference<>();
        try {
            Boolean delivered = transactionTemplate.execute(status -> {
                Optional<OutboxEventJpaEntity> next = repository.lockNextDue(now, properties.maxAttempts());
                if (next.isEmpty()) {
                    return false;
                }
                OutboxEventJpaEntity row = next.get();
                current.set(row.getId());
                publisher.publishEvent(new DeliveredEvent<>(deserialize(row)));
                row.markPublished(now);
                return true;
            });
            return Boolean.TRUE.equals(delivered) ? Outcome.DELIVERED : Outcome.NOTHING_DUE;
        } catch (RuntimeException e) {
            Long failedId = current.get();
            if (failedId == null) {
                throw e;   // the outbox itself is unreachable; the next run tries again
            }
            recordFailure(failedId, e, now);
            return Outcome.FAILED;
        }
    }

    private void recordFailure(long eventId, RuntimeException error, Instant now) {
        transactionTemplate.executeWithoutResult(status -> repository.findById(eventId).ifPresent(row -> {
            Duration backoff = Duration.ofSeconds(1L << Math.min(row.getAttempts(), 8));
            row.markFailed(error.getClass().getSimpleName() + ": " + error.getMessage(),
                now.plus(backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff));
            if (row.getAttempts() >= properties.maxAttempts()) {
                log.error("Outbox event given up after {} attempts: id={}, type={}", row.getAttempts(), eventId,
                    row.getEventType(), error);
            } else {
                log.warn("Outbox event delivery failed, will retry: id={}, type={}, attempt={}, cause={}", eventId,
                    row.getEventType(), row.getAttempts(), error.toString());
            }
        }));
    }

    private IntegrationEvent deserialize(OutboxEventJpaEntity row) {
        Class<?> type;
        try {
            type = Class.forName(row.getEventType());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unknown outbox event type: " + row.getEventType(), e);
        }
        // Only our own event types are ever instantiated from stored data.
        if (!IntegrationEvent.class.isAssignableFrom(type)) {
            throw new IllegalStateException("Not an integration event: " + row.getEventType());
        }
        return (IntegrationEvent) jsonMapper.readValue(row.getPayload(), type);
    }

    private enum Outcome {
        DELIVERED,
        FAILED,
        NOTHING_DUE
    }
}
