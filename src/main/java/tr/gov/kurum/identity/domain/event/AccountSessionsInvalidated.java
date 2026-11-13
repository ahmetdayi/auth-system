package tr.gov.kurum.identity.domain.event;

import java.time.Instant;
import java.util.Objects;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.DomainEvent;
import tr.gov.kurum.identity.domain.model.SessionInvalidationCause;

/** Every session of the account started at or before {@code occurredAt} is no longer valid. */
public record AccountSessionsInvalidated(AccountId account, SessionInvalidationCause cause, Instant occurredAt)
        implements DomainEvent {

    public AccountSessionsInvalidated {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
