package tr.gov.kurum.identity.application.service;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.identity.application.port.in.AccountAdministrationUseCase;
import tr.gov.kurum.identity.application.port.in.AccountSummaryView;
import tr.gov.kurum.identity.application.port.out.UserAccountRepository;
import tr.gov.kurum.identity.domain.model.UserAccount;

@Service
public class AccountAdministrationService implements AccountAdministrationUseCase {

    private final UserAccountRepository accountRepository;
    private final AccountLoader accounts;
    private final TransactionTemplate transactionTemplate;
    private final DomainEventDispatcher eventDispatcher;
    private final Clock clock;

    AccountAdministrationService(UserAccountRepository accountRepository, AccountLoader accounts,
                                 TransactionTemplate transactionTemplate, DomainEventDispatcher eventDispatcher,
                                 Clock clock) {
        this.accountRepository = accountRepository;
        this.accounts = accounts;
        this.transactionTemplate = transactionTemplate;
        this.eventDispatcher = eventDispatcher;
        this.clock = clock;
    }

    @Override
    public void disableCustomer(AccountId customer) {
        modifyCustomer(customer, account -> account.disable(clock.instant()));
    }

    @Override
    public void enableCustomer(AccountId customer) {
        modifyCustomer(customer, UserAccount::enable);
    }

    @Override
    public List<AccountSummaryView> findAccounts(Set<AccountId> ids) {
        if (ids.size() > MAX_LOOKUP_IDS) {
            throw new IllegalArgumentException("at most " + MAX_LOOKUP_IDS + " ids per lookup");
        }
        return accountRepository.findSummaries(ids);
    }

    private void modifyCustomer(AccountId customer, Consumer<UserAccount> change) {
        transactionTemplate.executeWithoutResult(status -> {
            UserAccount account = accounts.load(customer, AccountType.CUSTOMER);
            change.accept(account);
            accountRepository.save(account);
            eventDispatcher.dispatch(account.pullDomainEvents());
        });
    }
}
