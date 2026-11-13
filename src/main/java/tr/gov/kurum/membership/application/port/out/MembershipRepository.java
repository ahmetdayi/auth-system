package tr.gov.kurum.membership.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.membership.domain.model.Membership;

public interface MembershipRepository {

    Optional<Membership> findById(AccountId memberId);

    List<Membership> findAllById(Set<AccountId> memberIds);

    /** Throws {@link org.springframework.dao.OptimisticLockingFailureException} for a stale membership. */
    void save(Membership membership);

    void delete(Membership membership);
}
