package tr.gov.kurum.identity.infrastructure.adapter.out.membership;

import java.util.Optional;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.identity.application.port.out.MembershipLevelLookup;
import tr.gov.kurum.identity.domain.model.MembershipLevel;
import tr.gov.kurum.membership.application.port.in.MembershipQueryUseCase;

/**
 * Outbound adapter (rule 5.1): asks the membership context through its published inbound port. Only a tier
 * name crosses the boundary, so the identity model never depends on the membership model. If the membership
 * context is moved into its own service, only this class changes (it would call an HTTP API instead).
 */
@Component
class MembershipLevelAdapter implements MembershipLevelLookup {

    private final MembershipQueryUseCase memberships;

    MembershipLevelAdapter(MembershipQueryUseCase memberships) {
        this.memberships = memberships;
    }

    @Override
    public Optional<MembershipLevel> currentLevelOf(AccountId customer) {
        return memberships.currentTierName(customer).map(MembershipLevel::new);
    }
}
