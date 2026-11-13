package tr.gov.kurum.membership.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import tr.gov.kurum.common.domain.AccountId;

/**
 * Read side of the membership context. Also the published API other contexts call through their own
 * outbound adapters (rule 5.1): {@link #currentTierName} answers with a plain name, so a caller never needs
 * this context's {@code MembershipTier} type.
 */
public interface MembershipQueryUseCase {

    int MAX_LOOKUP_IDS = 100;

    Optional<MembershipView> membershipOf(AccountId member);

    List<MembershipView> membershipsOf(Set<AccountId> members);

    /** Name of the tier valid right now (e.g. {@code "GOLD"}); empty when there is none or it has ended. */
    Optional<String> currentTierName(AccountId member);
}
