package tr.gov.kurum.identity.application.port.in;

import java.util.List;
import java.util.Set;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.identity.domain.model.SupportGrantExtensionId;
import tr.gov.kurum.identity.domain.model.SupportGrantId;

/**
 * Consent-based support grants: a SUPPORT agent asks, the customer decides. Every method returns the grant
 * as it is after the change; issuing the act-as token is the caller's (adapter's) job.
 *
 * <p>A grant that is neither the caller's own nor visible to them is reported as not found.</p>
 */
public interface SupportGrantUseCase {

    /** Agent asks for access to a customer's account. {@code scopes} may be {@code null} (= defaults). */
    SupportGrantView request(AccountId agentId, AccountId customerId, Set<String> scopes);

    SupportGrantView approve(AccountId customerId, SupportGrantId grantId);

    SupportGrantView deny(AccountId customerId, SupportGrantId grantId);

    /** Agent asks for more time on an ACTIVE grant. */
    SupportGrantView requestExtension(AccountId agentId, SupportGrantId grantId);

    SupportGrantView approveExtension(AccountId customerId, SupportGrantId grantId, SupportGrantExtensionId extensionId);

    SupportGrantView denyExtension(AccountId customerId, SupportGrantId grantId, SupportGrantExtensionId extensionId);

    /**
     * Ends the grant. {@link AccountType#CUSTOMER} callers must be the grant's customer (CUSTOMER_ENDED),
     * {@link AccountType#SUPPORT} callers its agent (AGENT_ENDED); anything else is not found.
     */
    SupportGrantView end(AccountId callerId, AccountType callerType, SupportGrantId grantId);

    /** The agent's grants, newest first. */
    List<SupportGrantView> mine(AccountId agentId);

    /** Grants waiting for the customer's decision (PENDING or PENDING_EXTENSION). */
    List<SupportGrantView> incoming(AccountId customerId);

    /** All grants ever made for the customer, newest first. */
    List<SupportGrantView> history(AccountId customerId);

    SupportGrantView detail(AccountId callerId, AccountType callerType, SupportGrantId grantId);
}
