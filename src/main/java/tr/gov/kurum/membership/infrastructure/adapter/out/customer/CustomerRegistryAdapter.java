package tr.gov.kurum.membership.infrastructure.adapter.out.customer;

import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.customer.application.port.in.CustomerDirectoryUseCase;
import tr.gov.kurum.membership.application.port.out.CustomerRegistry;

/**
 * Asks the customer context through its published inbound port. This adapter is the only place in the
 * membership context that knows the customer context exists (rule 5.1).
 */
@Component
class CustomerRegistryAdapter implements CustomerRegistry {

    private final CustomerDirectoryUseCase customerDirectory;

    CustomerRegistryAdapter(CustomerDirectoryUseCase customerDirectory) {
        this.customerDirectory = customerDirectory;
    }

    @Override
    public boolean exists(AccountId customer) {
        return customerDirectory.exists(customer);
    }
}
