package tr.gov.kurum.identity.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.identity.application.port.in.AccountSummaryView;
import tr.gov.kurum.identity.domain.model.UserAccount;

public interface UserAccountRepository {

    AccountId nextId();

    Optional<UserAccount> findById(AccountId id);

    Optional<UserAccount> findByEmail(EmailAddress email, AccountType type);

    /** Read model straight from storage (rule 3.4). */
    List<AccountSummaryView> findSummaries(Set<AccountId> ids);

    /**
     * Inserts a new account or updates an existing one. Throws
     * {@link org.springframework.dao.OptimisticLockingFailureException} when the stored version differs from
     * the aggregate's, and {@code BusinessRuleException(EMAIL_ALREADY_REGISTERED)} when an account of the same
     * type already has the address.
     */
    void save(UserAccount account);
}
