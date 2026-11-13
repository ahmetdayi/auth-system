package tr.gov.kurum.common.infrastructure.adapter.in.messaging;

import java.util.Objects;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import tr.gov.kurum.common.domain.IntegrationEvent;

/**
 * An integration event as the outbox relay hands it to consumers. Consumers listen to this wrapper, e.g.
 * {@code @EventListener void on(DeliveredEvent<CustomerRegistered> delivered)}, never to the bare event:
 * the bare event is published inside the producer's transaction, before it is even committed.
 */
public record DeliveredEvent<E extends IntegrationEvent>(E event) implements ResolvableTypeProvider {

    public DeliveredEvent {
        Objects.requireNonNull(event, "event");
    }

    /** Lets Spring match listeners by the generic type ({@code DeliveredEvent<CustomerRegistered>}). */
    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClassWithGenerics(getClass(), ResolvableType.forInstance(event));
    }
}
