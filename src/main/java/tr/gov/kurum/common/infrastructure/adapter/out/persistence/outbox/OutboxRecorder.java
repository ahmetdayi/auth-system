package tr.gov.kurum.common.infrastructure.adapter.out.persistence.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;
import tr.gov.kurum.common.domain.IntegrationEvent;

/**
 * Writes every integration event into the outbox table in the transaction that produced it. BEFORE_COMMIT:
 * the row is committed together with the business change, or not at all.
 */
@Component
class OutboxRecorder {

    private final OutboxEventJpaRepository repository;
    private final JsonMapper jsonMapper;

    OutboxRecorder(OutboxEventJpaRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void record(IntegrationEvent event) {
        repository.save(new OutboxEventJpaEntity(repository.nextId(), event.getClass().getName(),
            jsonMapper.writeValueAsString(event), event.occurredAt()));
    }
}
