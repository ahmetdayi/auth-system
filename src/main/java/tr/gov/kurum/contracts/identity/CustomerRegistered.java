package tr.gov.kurum.contracts.identity;

import java.time.Instant;
import java.util.Objects;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.IntegrationEvent;

/**
 * The identity context opened a customer account. Delivered through the outbox; the customer context creates
 * the customer's profile from it.
 *
 * @param email     the login address, which the customer context keeps as the contact address
 * @param firstName as entered at registration; validated by the customer context, which owns names
 */
public record CustomerRegistered(
        AccountId accountId,
        String email,
        String firstName,
        String lastName,
        Instant occurredAt) implements IntegrationEvent {

    public CustomerRegistered {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** No personal data in logs. */
    @Override
    public String toString() {
        return "CustomerRegistered[accountId=" + accountId.value() + ", occurredAt=" + occurredAt + "]";
    }
}
