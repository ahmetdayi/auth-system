package tr.gov.kurum.membership.application.port.in;

import java.time.Instant;
import tr.gov.kurum.membership.domain.model.Membership;
import tr.gov.kurum.membership.domain.model.MembershipTier;

/** @param endsAt {@code null} for a membership without end date */
public record MembershipView(long memberId, MembershipTier tier, Instant startsAt, Instant endsAt, boolean validNow) {

    public static MembershipView of(Membership membership, Instant now) {
        return new MembershipView(membership.memberId().value(), membership.tier(),
            membership.period().startInclusive(), membership.period().end().orElse(null),
            membership.isValidAt(now));
    }
}
