package tr.gov.kurum.customer.application.port.in;

import java.util.Objects;
import tr.gov.kurum.common.domain.AccountId;

public record AddCustomerAddressCommand(AccountId customer, String label, String city, String line) {

    public AddCustomerAddressCommand {
        Objects.requireNonNull(customer, "customer");
    }
}
