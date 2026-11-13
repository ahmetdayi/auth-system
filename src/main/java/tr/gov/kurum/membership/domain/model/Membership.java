package tr.gov.kurum.membership.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AggregateRoot;

/**
 * A customer's membership: one per customer, identified by the customer's {@link AccountId}. Granting again
 * replaces the tier and the period; cancelling removes the membership.
 */
public final class Membership extends AggregateRoot {

    private final AccountId memberId;
    private MembershipTier tier;
    private ValidityPeriod period;

    private Membership(AccountId memberId, long version, MembershipTier tier, ValidityPeriod period) {
        super(version);
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.tier = Objects.requireNonNull(tier, "tier");
        this.period = Objects.requireNonNull(period, "period");
    }

    public static Membership grant(AccountId memberId, MembershipTier tier, ValidityPeriod period) {
        return new Membership(memberId, UNSAVED_VERSION, tier, period);
    }

    public static Membership restore(AccountId memberId, long version, MembershipTier tier, ValidityPeriod period) {
        return new Membership(memberId, version, tier, period);
    }

    public void regrant(MembershipTier newTier, ValidityPeriod newPeriod) {
        this.tier = Objects.requireNonNull(newTier, "newTier");
        this.period = Objects.requireNonNull(newPeriod, "newPeriod");
    }

    public boolean isValidAt(Instant instant) {
        return period.contains(instant);
    }

    /** The tier that grants something right now, if any. */
    public Optional<MembershipTier> tierValidAt(Instant instant) {
        return isValidAt(instant) ? Optional.of(tier) : Optional.empty();
    }

    public AccountId memberId() {
        return memberId;
    }

    public MembershipTier tier() {
        return tier;
    }

    public ValidityPeriod period() {
        return period;
    }
}
