package tr.gov.kurum.common.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base class for aggregate roots.
 * <ul>
 *   <li>Collects domain events until the application layer publishes them.</li>
 *   <li>Carries the optimistic-locking version read from storage, so a persistence adapter can reject
 *       a save based on stale state (lost update).</li>
 * </ul>
 */
public abstract class AggregateRoot {

    /** Version of an aggregate that has never been stored. */
    public static final long UNSAVED_VERSION = -1L;

    private final long version;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected AggregateRoot(long version) {
        if (version < UNSAVED_VERSION) {
            throw new IllegalArgumentException("invalid version: " + version);
        }
        this.version = version;
    }

    public long version() {
        return version;
    }

    public boolean isNew() {
        return version == UNSAVED_VERSION;
    }

    protected void registerEvent(DomainEvent event) {
        domainEvents.add(Objects.requireNonNull(event, "event"));
    }

    /** Returns the pending events and clears them, so each event is published exactly once. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}
