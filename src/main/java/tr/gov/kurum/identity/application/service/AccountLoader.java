package tr.gov.kurum.identity.application.service;

import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.identity.application.port.out.UserAccountRepository;
import tr.gov.kurum.identity.domain.model.IdentityErrorKey;
import tr.gov.kurum.identity.domain.model.UserAccount;

/**
 * Loads an account of an expected type. Accounts share one id space, so "customer #7" must be checked to
 * really be a customer; an account of the other type is reported exactly like a missing one.
 */
@Component
class AccountLoader {

    private final UserAccountRepository repository;

    AccountLoader(UserAccountRepository repository) {
        this.repository = repository;
    }

    UserAccount load(AccountId id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    UserAccount load(AccountId id, AccountType expectedType) {
        UserAccount account = load(id);
        if (!account.is(expectedType)) {
            throw notFound(id);
        }
        return account;
    }

    private static EntityNotFoundException notFound(AccountId id) {
        return new EntityNotFoundException(IdentityErrorKey.ACCOUNT_NOT_FOUND, id.value());
    }
}
