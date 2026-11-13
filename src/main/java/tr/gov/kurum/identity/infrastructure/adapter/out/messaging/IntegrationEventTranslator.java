package tr.gov.kurum.identity.infrastructure.adapter.out.messaging;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tr.gov.kurum.contracts.identity.CustomerRegistered;
import tr.gov.kurum.identity.domain.event.CustomerAccountOpened;

/**
 * Anti-corruption layer outwards: turns identity's domain events into the published language
 * ({@code tr.gov.kurum.contracts}). The integration event is recorded in the outbox by the shared kernel,
 * in the same transaction as the account.
 *
 * <p>A plain {@code @EventListener} on purpose: it runs immediately, inside the transaction the domain event
 * was dispatched in, so the outbox recorder can still join that transaction. Without a transaction the outbox
 * row would be dropped silently, so that case fails instead.</p>
 */
@Component
class IntegrationEventTranslator {

    private final ApplicationEventPublisher publisher;

    IntegrationEventTranslator(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener
    void on(CustomerAccountOpened event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("CustomerAccountOpened must be dispatched inside a transaction");
        }
        publisher.publishEvent(new CustomerRegistered(event.accountId(), event.email(), event.firstName(),
            event.lastName(), event.occurredAt()));
    }
}
