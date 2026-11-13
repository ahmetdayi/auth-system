package tr.gov.kurum.customer.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.customer.application.port.in.CustomerOnboardingUseCase;
import tr.gov.kurum.customer.application.port.in.OpenCustomerProfileCommand;
import tr.gov.kurum.customer.application.port.out.CustomerProfileRepository;
import tr.gov.kurum.customer.domain.model.CustomerProfile;
import tr.gov.kurum.customer.domain.model.PersonName;

/**
 * Runs inside the outbox relay's transaction: the profile and the "event delivered" mark are committed
 * together. A redelivery after a failure finds the profile and does nothing.
 */
@Service
public class CustomerOnboardingService implements CustomerOnboardingUseCase {

    private final CustomerProfileRepository repository;
    private final TransactionTemplate transactionTemplate;

    CustomerOnboardingService(CustomerProfileRepository repository, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public boolean openProfile(OpenCustomerProfileCommand command) {
        Boolean created = transactionTemplate.execute(status -> {
            if (repository.existsById(command.customer())) {
                return false;
            }
            repository.save(CustomerProfile.open(command.customer(), EmailAddress.of(command.contactEmail()),
                new PersonName(command.firstName(), command.lastName()), command.registeredAt()));
            return true;
        });
        return Boolean.TRUE.equals(created);
    }
}
