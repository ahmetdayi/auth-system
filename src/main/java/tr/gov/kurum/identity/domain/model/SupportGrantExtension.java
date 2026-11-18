package tr.gov.kurum.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import tr.gov.kurum.common.domain.BusinessRuleException;

/** Child of {@link SupportGrant}: one extension request and its decision. */
public final class SupportGrantExtension {

    private final SupportGrantExtensionId id;
    private final int requestedMinutes;
    private final Instant requestedAt;
    private SupportExtensionDecision decision;
    private Instant decidedAt;

    private SupportGrantExtension(SupportGrantExtensionId id, int requestedMinutes, Instant requestedAt,
                                  SupportExtensionDecision decision, Instant decidedAt) {
        this.id = Objects.requireNonNull(id, "id");
        if (requestedMinutes <= 0) {
            throw new IllegalArgumentException("requestedMinutes must be positive");
        }
        this.requestedMinutes = requestedMinutes;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.decidedAt = decidedAt;
    }

    static SupportGrantExtension request(SupportGrantExtensionId id, int minutes, Instant now) {
        return new SupportGrantExtension(id, minutes, now, SupportExtensionDecision.PENDING, null);
    }

    public static SupportGrantExtension restore(SupportGrantExtensionId id, int requestedMinutes,
                                                Instant requestedAt, SupportExtensionDecision decision,
                                                Instant decidedAt) {
        return new SupportGrantExtension(id, requestedMinutes, requestedAt, decision, decidedAt);
    }

    void approve(Instant now) {
        requirePending();
        this.decision = SupportExtensionDecision.APPROVED;
        this.decidedAt = Objects.requireNonNull(now, "now");
    }

    void deny(Instant now) {
        requirePending();
        this.decision = SupportExtensionDecision.DENIED;
        this.decidedAt = Objects.requireNonNull(now, "now");
    }

    private void requirePending() {
        if (decision != SupportExtensionDecision.PENDING) {
            throw new BusinessRuleException(IdentityErrorKey.SUPPORT_GRANT_INVALID_STATE, decision.name());
        }
    }

    public SupportGrantExtensionId id() {
        return id;
    }

    public int requestedMinutes() {
        return requestedMinutes;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public SupportExtensionDecision decision() {
        return decision;
    }

    public Instant decidedAt() {
        return decidedAt;
    }
}
