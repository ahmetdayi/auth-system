package tr.gov.kurum.identity.application.port.out;

import java.util.Optional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.identity.domain.model.MembershipLevel;

/**
 * The membership a customer holds right now, owned by the membership context. Outbound port (rule 5.1): the
 * identity context declares what it needs; an adapter asks the membership context.
 */
public interface MembershipLevelLookup {

    Optional<MembershipLevel> currentLevelOf(AccountId customer);
}
