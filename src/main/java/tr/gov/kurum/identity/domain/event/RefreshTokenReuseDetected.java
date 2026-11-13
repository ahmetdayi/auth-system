package tr.gov.kurum.identity.domain.event;

import java.time.Instant;
import java.util.Objects;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.DomainEvent;
import tr.gov.kurum.identity.domain.model.SessionId;

/** A refresh token that had already been rotated was presented again: the token chain was copied. */
public record RefreshTokenReuseDetected(AccountId account, SessionId sessionId, Instant occurredAt)
        implements DomainEvent {

    public RefreshTokenReuseDetected {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
