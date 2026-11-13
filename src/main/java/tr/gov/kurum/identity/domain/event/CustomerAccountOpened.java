package tr.gov.kurum.identity.domain.event;

import java.time.Instant;
import java.util.Objects;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.DomainEvent;

/**
 * A customer account was opened. Identity's own domain event; an outbound adapter translates it into the
 * published {@code CustomerRegistered} integration event, so other contexts never depend on this class.
 *
 * @param firstName as entered at registration; the identity context does not keep names
 */
public record CustomerAccountOpened(
        AccountId accountId,
        String email,
        String firstName,
        String lastName,
        Instant occurredAt) implements DomainEvent {

    public CustomerAccountOpened {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** No personal data in logs. */
    @Override
    public String toString() {
        return "CustomerAccountOpened[accountId=" + accountId.value() + ", occurredAt=" + occurredAt + "]";
    }
}
