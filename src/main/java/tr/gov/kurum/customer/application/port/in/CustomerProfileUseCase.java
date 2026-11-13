package tr.gov.kurum.customer.application.port.in;

import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.customer.domain.model.AddressId;

public interface CustomerProfileUseCase {

    CustomerProfileView getProfile(AccountId customer);

    void rename(RenameCustomerCommand command);

    AddressId addAddress(AddCustomerAddressCommand command);

    void removeAddress(AccountId customer, AddressId addressId);
}
