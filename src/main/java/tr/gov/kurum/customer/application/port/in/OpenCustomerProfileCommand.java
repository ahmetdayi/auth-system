package tr.gov.kurum.customer.application.port.in;

import java.time.Instant;
import java.util.Objects;
import tr.gov.kurum.common.domain.AccountId;

public record OpenCustomerProfileCommand(AccountId customer, String contactEmail, String firstName,
                                         String lastName, Instant registeredAt) {

    public OpenCustomerProfileCommand {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }

    @Override
    public String toString() {
        return "OpenCustomerProfileCommand[customer=" + customer.value() + "]";
    }
}
