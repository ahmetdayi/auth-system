package tr.gov.kurum.membership.application.port.in;

import java.time.Instant;
import java.util.Objects;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.membership.domain.model.MembershipTier;

/** @param validUntil exclusive end; {@code null} for a membership without end date */
public record GrantMembershipCommand(AccountId memberId, MembershipTier tier, Instant validUntil) {

    public GrantMembershipCommand {
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(tier, "tier");
    }
}
