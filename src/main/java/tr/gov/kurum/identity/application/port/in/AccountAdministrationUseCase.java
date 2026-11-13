package tr.gov.kurum.identity.application.port.in;

import java.util.List;
import java.util.Set;
import tr.gov.kurum.common.domain.AccountId;

/** Admin operations on customer accounts. Authorization is enforced by the web adapter. */
public interface AccountAdministrationUseCase {

    int MAX_LOOKUP_IDS = 100;

    /** Ban: the account is disabled, every session ends and every access token stops working at once. */
    void disableCustomer(AccountId customer);

    void enableCustomer(AccountId customer);

    /** Login data of several accounts at once, for screens that combine data of several contexts. */
    List<AccountSummaryView> findAccounts(Set<AccountId> ids);
}
