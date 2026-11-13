package tr.gov.kurum.membership.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.BusinessRuleException;

class MembershipTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final AccountId MEMBER = new AccountId(10);

    @Test
    void a_membership_grants_its_tier_only_inside_its_half_open_period() {
        Membership membership = Membership.grant(MEMBER, MembershipTier.GOLD,
            new ValidityPeriod(T0, T0.plusSeconds(100)));

        assertThat(membership.tierValidAt(T0.minusMillis(1))).isEmpty();
        assertThat(membership.tierValidAt(T0)).contains(MembershipTier.GOLD);
        assertThat(membership.tierValidAt(T0.plusSeconds(99))).contains(MembershipTier.GOLD);
        assertThat(membership.tierValidAt(T0.plusSeconds(100))).isEmpty();   // end is exclusive
    }

    @Test
    void an_open_ended_membership_never_expires() {
        Membership membership = Membership.grant(MEMBER, MembershipTier.SILVER, ValidityPeriod.openEnded(T0));

        assertThat(membership.isValidAt(T0.plusSeconds(10L * 365 * 24 * 3600))).isTrue();
        assertThat(membership.period().end()).isEmpty();
    }

    @Test
    void granting_again_replaces_tier_and_period() {
        Membership membership = Membership.grant(MEMBER, MembershipTier.BRONZE, ValidityPeriod.openEnded(T0));

        membership.regrant(MembershipTier.GOLD, new ValidityPeriod(T0.plusSeconds(10), T0.plusSeconds(20)));

        assertThat(membership.tier()).isEqualTo(MembershipTier.GOLD);
        assertThat(membership.isValidAt(T0)).isFalse();
        assertThat(membership.isValidAt(T0.plusSeconds(15))).isTrue();
    }

    @Test
    void the_end_must_be_after_the_start() {
        assertThatThrownBy(() -> new ValidityPeriod(T0, T0))
            .isInstanceOf(BusinessRuleException.class)
            .extracting(e -> ((BusinessRuleException) e).errorKey())
            .isEqualTo(MembershipErrorKey.INVALID_MEMBERSHIP_PERIOD);
    }

    @Test
    void tiers_are_ordered_from_lowest_to_highest() {
        // MembershipAuthorityHierarchy and the content rules rely on this order.
        assertThat(MembershipTier.values()).containsExactly(MembershipTier.BRONZE, MembershipTier.SILVER,
            MembershipTier.GOLD);
    }
}
