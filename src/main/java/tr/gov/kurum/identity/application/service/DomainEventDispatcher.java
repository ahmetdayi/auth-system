package tr.gov.kurum.identity.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.DomainEvent;

/**
 * Publishes domain events through Spring. Call it inside the transaction that stored the aggregate: listeners
 * are {@code @TransactionalEventListener}s, so they are bound to that transaction's outcome (nothing happens
 * if it rolls back). Each listener chooses its phase; a security side effect uses BEFORE_COMMIT so that its
 * failure rolls the change back.
 */
@Component
@RequiredArgsConstructor
public class DomainEventDispatcher {

    private final ApplicationEventPublisher publisher;

    public void dispatch(List<DomainEvent> events) {
        events.forEach(publisher::publishEvent);
    }
}
