package tr.gov.kurum.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.BusinessRuleException;
import tr.gov.kurum.common.domain.PermissionCodes;

class SupportGrantTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final AccountId AGENT = new AccountId(10);
    private static final AccountId CUSTOMER = new AccountId(20);

    private final AtomicLong extensionSequence = new AtomicLong();
    private final Supplier<SupportGrantExtensionId> nextExtensionId =
        () -> new SupportGrantExtensionId(extensionSequence.incrementAndGet());

    private SupportGrant pendingGrant() {
        return SupportGrant.request(new SupportGrantId(1), AGENT, CUSTOMER, null, T0);
    }

    private SupportGrant activeGrant() {
        SupportGrant grant = pendingGrant();
        grant.approve(T0.plusSeconds(30));
        return grant;
    }

    @Test
    void request_starts_pending_with_default_scopes_and_no_expiry() {
        SupportGrant grant = pendingGrant();

        assertThat(grant.status()).isEqualTo(SupportGrantStatus.PENDING);
        assertThat(grant.isNew()).isTrue();
        assertThat(grant.isLive()).isTrue();
        assertThat(grant.scopes()).containsExactlyInAnyOrderElementsOf(SupportScopes.DEFAULT);
        assertThat(grant.initialMinutes()).isEqualTo(SupportGrant.DEFAULT_INITIAL_MINUTES);
        assertThat(grant.expiresAt()).isNull();
        assertThat(grant.startedAt()).isNull();
    }

    @Test
    void request_accepts_a_subset_of_scopes_and_rejects_unknown_ones() {
        SupportGrant readOnly = SupportGrant.request(new SupportGrantId(1), AGENT, CUSTOMER,
            Set.of(PermissionCodes.SUPPORT_PROFILE_READ), T0);

        assertThat(readOnly.scopes()).containsExactly(PermissionCodes.SUPPORT_PROFILE_READ);
        assertThatThrownBy(() -> SupportGrant.request(new SupportGrantId(2), AGENT, CUSTOMER,
            Set.of("customer:write"), T0))
            .isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.errorKey()).isEqualTo(IdentityErrorKey.SUPPORT_SCOPE_INVALID));
    }

    @Test
    void catalog_lists_only_whitelisted_scopes_and_all_matches_default() {
        assertThat(SupportScopes.CATALOG).extracting(SupportScopes.Entry::code)
            .containsExactlyElementsOf(SupportScopes.DEFAULT);
        assertThat(SupportScopes.all()).containsExactlyElementsOf(SupportScopes.DEFAULT);
    }

    @Test
    void full_flow_request_approve_extend_approve_extension_end() {
        SupportGrant grant = pendingGrant();

        Instant approvedAt = T0.plusSeconds(30);
        grant.approve(approvedAt);
        assertThat(grant.status()).isEqualTo(SupportGrantStatus.ACTIVE);
        assertThat(grant.startedAt()).isEqualTo(approvedAt);
        assertThat(grant.expiresAt()).isEqualTo(approvedAt.plus(Duration.ofMinutes(20)));

        Instant extensionRequestedAt = T0.plus(Duration.ofMinutes(10));
        SupportGrantExtension extension = grant.requestExtension(nextExtensionId, extensionRequestedAt);
        assertThat(grant.status()).isEqualTo(SupportGrantStatus.PENDING_EXTENSION);
        assertThat(extension.decision()).isEqualTo(SupportExtensionDecision.PENDING);
        assertThat(extension.requestedMinutes()).isEqualTo(SupportGrant.DEFAULT_EXTENSION_MINUTES);

        Instant decidedAt = T0.plus(Duration.ofMinutes(12));
        grant.approveExtension(extension.id(), decidedAt);
        assertThat(grant.status()).isEqualTo(SupportGrantStatus.ACTIVE);
        assertThat(grant.extensionCount()).isEqualTo(1);
        assertThat(grant.totalApprovedExtensionMinutes()).isEqualTo(20);
        assertThat(grant.extensions().get(0).decision()).isEqualTo(SupportExtensionDecision.APPROVED);
        assertThat(grant.extensions().get(0).decidedAt()).isEqualTo(decidedAt);
        // The extension is added on top of the remaining time, not of "now".
        assertThat(grant.expiresAt()).isEqualTo(approvedAt.plus(Duration.ofMinutes(40)));

        Instant endedAt = T0.plus(Duration.ofMinutes(15));
        grant.end(SupportEndReason.CUSTOMER_ENDED, endedAt);
        assertThat(grant.status()).isEqualTo(SupportGrantStatus.ENDED);
        assertThat(grant.endReason()).isEqualTo(SupportEndReason.CUSTOMER_ENDED);
        assertThat(grant.endedAt()).isEqualTo(endedAt);
        assertThat(grant.isLive()).isFalse();
        // History stays for audit.
        assertThat(grant.extensions()).hasSize(1);
    }

    @Test
    void agent_can_end_an_active_grant() {
        SupportGrant grant = activeGrant();

        grant.end(SupportEndReason.AGENT_ENDED, T0.plus(Duration.ofMinutes(2)));

        assertThat(grant.status()).isEqualTo(SupportGrantStatus.ENDED);
        assertThat(grant.endReason()).isEqualTo(SupportEndReason.AGENT_ENDED);
    }

    @Test
    void ending_twice_changes_nothing() {
        SupportGrant grant = activeGrant();
        Instant firstEnd = T0.plus(Duration.ofMinutes(2));
        grant.end(SupportEndReason.CUSTOMER_ENDED, firstEnd);

        grant.end(SupportEndReason.AGENT_ENDED, T0.plus(Duration.ofMinutes(3)));

        assertThat(grant.endReason()).isEqualTo(SupportEndReason.CUSTOMER_ENDED);
        assertThat(grant.endedAt()).isEqualTo(firstEnd);
    }

    @Test
    void ending_a_pending_grant_counts_as_denied() {
        SupportGrant grant = pendingGrant();

        grant.end(SupportEndReason.AGENT_ENDED, T0.plusSeconds(5));

        assertThat(grant.status()).isEqualTo(SupportGrantStatus.DENIED);
        assertThat(grant.endReason()).isEqualTo(SupportEndReason.DENIED);
    }

    @Test
    void deny_ends_a_pending_grant_and_it_can_not_be_approved_afterwards() {
        SupportGrant grant = pendingGrant();
        Instant deniedAt = T0.plusSeconds(20);

        grant.deny(deniedAt);

        assertThat(grant.status()).isEqualTo(SupportGrantStatus.DENIED);
        assertThat(grant.endReason()).isEqualTo(SupportEndReason.DENIED);
        assertThat(grant.endedAt()).isEqualTo(deniedAt);
        assertThat(grant.isLive()).isFalse();
        assertThatThrownBy(() -> grant.approve(T0.plusSeconds(30)))
            .isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.errorKey()).isEqualTo(IdentityErrorKey.SUPPORT_GRANT_INVALID_STATE));
    }

    @Test
    void an_active_grant_can_not_be_approved_or_denied_again() {
        SupportGrant grant = activeGrant();

        assertThatThrownBy(() -> grant.approve(T0.plusSeconds(60))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> grant.deny(T0.plusSeconds(60))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void denied_extension_keeps_the_grant_active_with_its_old_expiry() {
        SupportGrant grant = activeGrant();
        Instant expiry = grant.expiresAt();
        SupportGrantExtension extension = grant.requestExtension(nextExtensionId, T0.plus(Duration.ofMinutes(5)));

        grant.denyExtension(extension.id(), T0.plus(Duration.ofMinutes(6)));

        assertThat(grant.status()).isEqualTo(SupportGrantStatus.ACTIVE);
        assertThat(grant.expiresAt()).isEqualTo(expiry);
        assertThat(grant.extensionCount()).isZero();
        assertThat(grant.totalApprovedExtensionMinutes()).isZero();
        assertThat(grant.extensions().get(0).decision()).isEqualTo(SupportExtensionDecision.DENIED);
    }

    @Test
    void only_one_extension_can_be_pending_and_it_needs_an_active_grant() {
        SupportGrant pending = pendingGrant();
        assertThatThrownBy(() -> pending.requestExtension(nextExtensionId, T0.plusSeconds(1)))
            .isInstanceOf(BusinessRuleException.class);

        SupportGrant grant = activeGrant();
        grant.requestExtension(nextExtensionId, T0.plus(Duration.ofMinutes(5)));
        // Status is PENDING_EXTENSION now, so a second request is refused as invalid state.
        assertThatThrownBy(() -> grant.requestExtension(nextExtensionId, T0.plus(Duration.ofMinutes(6))))
            .isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.errorKey()).isEqualTo(IdentityErrorKey.SUPPORT_GRANT_INVALID_STATE));
    }

    @Test
    void unknown_extension_id_is_reported_as_not_found() {
        SupportGrant grant = activeGrant();
        grant.requestExtension(nextExtensionId, T0.plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> grant.approveExtension(new SupportGrantExtensionId(999),
            T0.plus(Duration.ofMinutes(6))))
            .isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.errorKey()).isEqualTo(IdentityErrorKey.SUPPORT_GRANT_NOT_FOUND));
    }

    @Test
    void extensions_are_capped() {
        SupportGrant grant = activeGrant();
        Instant now = T0.plusSeconds(60);
        for (int i = 0; i < SupportGrant.MAX_EXTENSIONS; i++) {
            SupportGrantExtension extension = grant.requestExtension(nextExtensionId, now);
            grant.approveExtension(extension.id(), now);
        }
        assertThat(grant.extensionCount()).isEqualTo(SupportGrant.MAX_EXTENSIONS);
        assertThat(grant.totalApprovedExtensionMinutes())
            .isEqualTo(SupportGrant.MAX_EXTENSIONS * SupportGrant.DEFAULT_EXTENSION_MINUTES);

        assertThatThrownBy(() -> grant.requestExtension(nextExtensionId, now))
            .isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.errorKey()).isEqualTo(IdentityErrorKey.SUPPORT_EXTENSION_LIMIT));
        assertThat(grant.status()).isEqualTo(SupportGrantStatus.ACTIVE);
    }

    @Test
    void active_grant_expires_at_its_expiry_and_is_then_closed_for_changes() {
        SupportGrant grant = activeGrant();
        Instant expiry = grant.expiresAt();

        assertThat(grant.expireIfNeeded(expiry.minusSeconds(1))).isFalse();
        assertThat(grant.status()).isEqualTo(SupportGrantStatus.ACTIVE);

        assertThat(grant.expireIfNeeded(expiry.plusSeconds(5))).isTrue();
        assertThat(grant.status()).isEqualTo(SupportGrantStatus.EXPIRED);
        assertThat(grant.endReason()).isEqualTo(SupportEndReason.EXPIRED);
        // The recorded end is the moment the grant ran out, not the moment somebody noticed.
        assertThat(grant.endedAt()).isEqualTo(expiry);
        assertThat(grant.isLive()).isFalse();
        // Idempotent.
        assertThat(grant.expireIfNeeded(expiry.plusSeconds(60))).isFalse();

        assertThatThrownBy(() -> grant.requestExtension(nextExtensionId, expiry.plusSeconds(10)))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void a_pending_extension_does_not_keep_an_expired_grant_alive() {
        SupportGrant grant = activeGrant();
        SupportGrantExtension extension = grant.requestExtension(nextExtensionId, T0.plus(Duration.ofMinutes(10)));
        Instant afterExpiry = grant.expiresAt().plusSeconds(1);

        assertThatThrownBy(() -> grant.approveExtension(extension.id(), afterExpiry))
            .isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.errorKey()).isEqualTo(IdentityErrorKey.SUPPORT_GRANT_INVALID_STATE));
        assertThat(grant.status()).isEqualTo(SupportGrantStatus.EXPIRED);
    }

    @Test
    void ending_an_expired_grant_changes_nothing() {
        SupportGrant grant = activeGrant();
        Instant expiry = grant.expiresAt();

        grant.end(SupportEndReason.CUSTOMER_ENDED, expiry.plusSeconds(30));

        assertThat(grant.status()).isEqualTo(SupportGrantStatus.EXPIRED);
        assertThat(grant.endReason()).isEqualTo(SupportEndReason.EXPIRED);
    }

    @Test
    void agent_and_customer_must_differ() {
        assertThatThrownBy(() -> SupportGrant.request(new SupportGrantId(1), AGENT, AGENT, null, T0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void support_token_authorities_are_customer_type_plus_scopes() {
        SupportGrant grant = activeGrant();

        assertThat(grant.authoritiesForSupportToken())
            .contains("TYPE_CUSTOMER")
            .containsAll(SupportScopes.DEFAULT)
            .doesNotContain("TYPE_SUPPORT");
    }
}
