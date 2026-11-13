package tr.gov.kurum.customer.domain.model;

import java.time.Instant;
import java.util.List;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.EmailAddress;

/** Stored state of a {@link CustomerProfile}, used to restore it. */
public record CustomerProfileSnapshot(
        AccountId id,
        long version,
        EmailAddress contactEmail,
        PersonName name,
        int loyaltyPoints,
        List<Address> addresses,
        Instant registeredAt) {

    public CustomerProfileSnapshot {
        addresses = List.copyOf(addresses);
    }
}
