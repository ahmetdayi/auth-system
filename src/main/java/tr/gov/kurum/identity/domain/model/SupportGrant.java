package tr.gov.kurum.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AggregateRoot;
import tr.gov.kurum.common.domain.BusinessRuleException;

/**
 * Consent-based timed grant for a SUPPORT agent to act as a customer. Extensions are children; history is
 * kept for audit even after the grant ends.
 */
public final class SupportGrant extends AggregateRoot {

    public static final int DEFAULT_INITIAL_MINUTES = 20;
    public static final int DEFAULT_EXTENSION_MINUTES = 20;
    public static final int MAX_EXTENSIONS = 3;

    private final SupportGrantId id;
    private final AccountId agentId;
    private final AccountId customerId;
    private final Set<String> scopes;
    private final int initialMinutes;
    private final Instant requestedAt;
    private final List<SupportGrantExtension> extensions;

    private SupportGrantStatus status;
    private int extensionCount;
    private Instant expiresAt;
    private Instant startedAt;
    private Instant endedAt;
    private SupportEndReason endReason;

    private SupportGrant(SupportGrantId id, long version, AccountId agentId, AccountId customerId,
                         Set<String> scopes, int initialMinutes, SupportGrantStatus status, int extensionCount,
                         Instant expiresAt, Instant requestedAt, Instant startedAt, Instant endedAt,
                         SupportEndReason endReason, List<SupportGrantExtension> extensions) {
        super(version);
        this.id = Objects.requireNonNull(id, "id");
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        if (agentId.equals(customerId)) {
            throw new IllegalArgumentException("agent and customer must differ");
        }
        this.scopes = Set.copyOf(SupportScopes.requireValid(scopes));
        if (initialMinutes <= 0) {
            throw new IllegalArgumentException("initialMinutes must be positive");
        }
        this.initialMinutes = initialMinutes;
        this.status = Objects.requireNonNull(status, "status");
        this.extensionCount = extensionCount;
        this.expiresAt = expiresAt;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.endReason = endReason;
        this.extensions = new ArrayList<>(extensions);
    }

    public static SupportGrant request(SupportGrantId id, AccountId agentId, AccountId customerId,
                                       Set<String> scopes, Instant now) {
        return new SupportGrant(id, UNSAVED_VERSION, agentId, customerId, scopes, DEFAULT_INITIAL_MINUTES,
            SupportGrantStatus.PENDING, 0, null, now, null, null, null, List.of());
    }

    public static SupportGrant restore(SupportGrantId id, long version, AccountId agentId, AccountId customerId,
                                       Set<String> scopes, int initialMinutes, SupportGrantStatus status,
                                       int extensionCount, Instant expiresAt, Instant requestedAt,
                                       Instant startedAt, Instant endedAt, SupportEndReason endReason,
                                       List<SupportGrantExtension> extensions) {
        return new SupportGrant(id, version, agentId, customerId, scopes, initialMinutes, status, extensionCount,
            expiresAt, requestedAt, startedAt, endedAt, endReason, extensions);
    }

    /** Customer approves a PENDING grant → ACTIVE with {@code expiresAt = now + initialMinutes}. */
    public void approve(Instant now) {
        expireIfNeeded(now);
        requireStatus(SupportGrantStatus.PENDING);
        this.status = SupportGrantStatus.ACTIVE;
        this.startedAt = now;
        this.expiresAt = now.plus(Duration.ofMinutes(initialMinutes));
    }

    public void deny(Instant now) {
        expireIfNeeded(now);
        requireStatus(SupportGrantStatus.PENDING);
        this.status = SupportGrantStatus.DENIED;
        this.endedAt = now;
        this.endReason = SupportEndReason.DENIED;
    }

    public SupportGrantExtension requestExtension(Supplier<SupportGrantExtensionId> nextId, Instant now) {
        expireIfNeeded(now);
        requireStatus(SupportGrantStatus.ACTIVE);
        if (extensionCount >= MAX_EXTENSIONS) {
            throw new BusinessRuleException(IdentityErrorKey.SUPPORT_EXTENSION_LIMIT, MAX_EXTENSIONS);
        }
        if (findPendingExtension().isPresent()) {
            throw new BusinessRuleException(IdentityErrorKey.SUPPORT_GRANT_INVALID_STATE, "extension pending");
        }
        SupportGrantExtension extension =
            SupportGrantExtension.request(nextId.get(), DEFAULT_EXTENSION_MINUTES, now);
        extensions.add(extension);
        this.status = SupportGrantStatus.PENDING_EXTENSION;
        return extension;
    }

    public void approveExtension(SupportGrantExtensionId extensionId, Instant now) {
        expireIfNeeded(now);
        requireStatus(SupportGrantStatus.PENDING_EXTENSION);
        SupportGrantExtension extension = requireExtension(extensionId);
        extension.approve(now);
        this.extensionCount++;
        Instant base = expiresAt != null && expiresAt.isAfter(now) ? expiresAt : now;
        this.expiresAt = base.plus(Duration.ofMinutes(extension.requestedMinutes()));
        this.status = SupportGrantStatus.ACTIVE;
    }

    public void denyExtension(SupportGrantExtensionId extensionId, Instant now) {
        expireIfNeeded(now);
        requireStatus(SupportGrantStatus.PENDING_EXTENSION);
        requireExtension(extensionId).deny(now);
        this.status = SupportGrantStatus.ACTIVE;
    }

    public void end(SupportEndReason reason, Instant now) {
        Objects.requireNonNull(reason, "reason");
        expireIfNeeded(now);
        if (status == SupportGrantStatus.ENDED || status == SupportGrantStatus.EXPIRED
                || status == SupportGrantStatus.DENIED) {
            return;
        }
        if (status != SupportGrantStatus.ACTIVE && status != SupportGrantStatus.PENDING_EXTENSION
                && status != SupportGrantStatus.PENDING) {
            throw new BusinessRuleException(IdentityErrorKey.SUPPORT_GRANT_INVALID_STATE, status.name());
        }
        if (status == SupportGrantStatus.PENDING) {
            this.status = SupportGrantStatus.DENIED;
            this.endReason = SupportEndReason.DENIED;
        } else {
            this.status = SupportGrantStatus.ENDED;
            this.endReason = reason;
        }
        this.endedAt = now;
    }

    /** Marks EXPIRED when past {@code expiresAt}; idempotent. */
    public boolean expireIfNeeded(Instant now) {
        if ((status == SupportGrantStatus.ACTIVE || status == SupportGrantStatus.PENDING_EXTENSION)
                && expiresAt != null && !expiresAt.isAfter(now)) {
            this.status = SupportGrantStatus.EXPIRED;
            this.endedAt = expiresAt;
            this.endReason = SupportEndReason.EXPIRED;
            return true;
        }
        return false;
    }

    public boolean isLive() {
        return status == SupportGrantStatus.PENDING || status == SupportGrantStatus.ACTIVE
            || status == SupportGrantStatus.PENDING_EXTENSION;
    }

    public int totalApprovedExtensionMinutes() {
        return extensions.stream()
            .filter(e -> e.decision() == SupportExtensionDecision.APPROVED)
            .mapToInt(SupportGrantExtension::requestedMinutes)
            .sum();
    }

    private void requireStatus(SupportGrantStatus expected) {
        if (status != expected) {
            throw new BusinessRuleException(IdentityErrorKey.SUPPORT_GRANT_INVALID_STATE, status.name());
        }
    }

    private SupportGrantExtension requireExtension(SupportGrantExtensionId extensionId) {
        return extensions.stream()
            .filter(e -> e.id().equals(extensionId))
            .findFirst()
            .orElseThrow(() -> new BusinessRuleException(IdentityErrorKey.SUPPORT_GRANT_NOT_FOUND,
                extensionId.value()));
    }

    private Optional<SupportGrantExtension> findPendingExtension() {
        return extensions.stream()
            .filter(e -> e.decision() == SupportExtensionDecision.PENDING)
            .findFirst();
    }

    public SupportGrantId id() {
        return id;
    }

    public AccountId agentId() {
        return agentId;
    }

    public AccountId customerId() {
        return customerId;
    }

    public Set<String> scopes() {
        return scopes;
    }

    public int initialMinutes() {
        return initialMinutes;
    }

    public SupportGrantStatus status() {
        return status;
    }

    public int extensionCount() {
        return extensionCount;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public SupportEndReason endReason() {
        return endReason;
    }

    public List<SupportGrantExtension> extensions() {
        return List.copyOf(extensions);
    }

    public Set<String> authoritiesForSupportToken() {
        Set<String> authorities = new LinkedHashSet<>();
        authorities.add("TYPE_CUSTOMER");
        authorities.addAll(scopes);
        return authorities;
    }
}
