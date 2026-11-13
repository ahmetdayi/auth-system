package tr.gov.kurum.membership.domain.model;

/** Membership tiers in ascending order. A higher tier includes every lower tier. */
public enum MembershipTier {
    BRONZE,
    SILVER,
    GOLD;

    public boolean includes(MembershipTier other) {
        return this.ordinal() >= other.ordinal();
    }
}
