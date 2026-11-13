package tr.gov.kurum.customer.application.service;

import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.customer.application.port.in.AddCustomerAddressCommand;
import tr.gov.kurum.customer.application.port.in.CustomerProfileUseCase;
import tr.gov.kurum.customer.application.port.in.CustomerProfileView;
import tr.gov.kurum.customer.application.port.in.RenameCustomerCommand;
import tr.gov.kurum.customer.application.port.out.CustomerProfileRepository;
import tr.gov.kurum.customer.domain.model.AddressId;
import tr.gov.kurum.customer.domain.model.CustomerErrorKey;
import tr.gov.kurum.customer.domain.model.CustomerProfile;
import tr.gov.kurum.customer.domain.model.PersonName;

@Service
public class CustomerProfileService implements CustomerProfileUseCase {

    private final CustomerProfileRepository repository;
    private final TransactionTemplate transactionTemplate;

    CustomerProfileService(CustomerProfileRepository repository, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public CustomerProfileView getProfile(AccountId customer) {
        return CustomerProfileView.of(load(customer));
    }

    @Override
    public void rename(RenameCustomerCommand command) {
        PersonName newName = new PersonName(command.firstName(), command.lastName());
        modify(command.customer(), profile -> profile.rename(newName));
    }

    @Override
    public AddressId addAddress(AddCustomerAddressCommand command) {
        AddressId addressId = repository.nextAddressId();
        modify(command.customer(), profile -> profile.addAddress(addressId, command.label(), command.city(),
            command.line()));
        return addressId;
    }

    @Override
    public void removeAddress(AccountId customer, AddressId addressId) {
        modify(customer, profile -> profile.removeAddress(addressId));
    }

    private void modify(AccountId customer, Consumer<CustomerProfile> change) {
        transactionTemplate.executeWithoutResult(status -> {
            CustomerProfile profile = load(customer);
            change.accept(profile);
            repository.save(profile);
        });
    }

    private CustomerProfile load(AccountId customer) {
        return repository.findById(customer)
            .orElseThrow(() -> new EntityNotFoundException(CustomerErrorKey.CUSTOMER_NOT_FOUND, customer.value()));
    }
}
