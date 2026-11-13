package tr.gov.kurum.common.domain;

import java.time.Instant;

/** Marker for facts that happened in the domain. Events carry identifiers, never whole aggregates. */
public interface DomainEvent {

    Instant occurredAt();
}
