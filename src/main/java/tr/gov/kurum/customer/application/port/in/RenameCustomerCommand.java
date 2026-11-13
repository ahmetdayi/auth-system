package tr.gov.kurum.customer.application.port.in;

import java.util.Objects;
import tr.gov.kurum.common.domain.AccountId;

public record RenameCustomerCommand(AccountId customer, String firstName, String lastName) {

    public RenameCustomerCommand {
        Objects.requireNonNull(customer, "customer");
    }
}
