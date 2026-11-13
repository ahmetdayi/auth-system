package tr.gov.kurum.identity.application.port.in;

import tr.gov.kurum.common.domain.AccountId;

public interface CustomerRegistrationUseCase {

    /**
     * Opens the account; the customer profile follows through the {@code CustomerAccountOpened} event
     * (outbox), so it can appear a moment after this returns.
     */
    AccountId register(RegisterCustomerCommand command);
}
