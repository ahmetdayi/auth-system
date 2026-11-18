package tr.gov.kurum.identity.application.port.out;

import java.util.List;
import java.util.Optional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.identity.domain.model.SupportGrant;
import tr.gov.kurum.identity.domain.model.SupportGrantExtensionId;
import tr.gov.kurum.identity.domain.model.SupportGrantId;

public interface SupportGrantRepository {

    SupportGrantId nextId();

    SupportGrantExtensionId nextExtensionId();

    Optional<SupportGrant> findById(SupportGrantId id);

    /**
     * Inserts a new grant or updates an existing one together with its extensions. Throws
     * {@link org.springframework.dao.OptimisticLockingFailureException} when the stored version differs from
     * the aggregate's, and {@code BusinessRuleException(SUPPORT_GRANT_CONFLICT)} when the customer already has
     * a live grant.
     */
    void save(SupportGrant grant);

    /** True when the customer has a PENDING, ACTIVE or PENDING_EXTENSION grant (as stored). */
    boolean hasLiveGrantForCustomer(AccountId customerId);

    /** The agent's grants, newest first, bounded. */
    List<SupportGrant> findByAgent(AccountId agentId);

    /** The customer's grants, newest first, bounded. */
    List<SupportGrant> findByCustomer(AccountId customerId);
}
