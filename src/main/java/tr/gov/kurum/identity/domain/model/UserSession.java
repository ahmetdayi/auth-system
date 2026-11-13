package tr.gov.kurum.identity.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AggregateRoot;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.identity.domain.event.RefreshTokenReuseDetected;

/**
 * One login = one session. Every refresh rotates the session's refresh token; rotated tokens are retired
 * (not deleted) so that presenting one again can be recognised as theft.
 *
 * <p>The whole refresh decision lives here, so the application service only orchestrates.</p>
 */
public final class UserSession extends AggregateRoot {

    private final SessionId id;
    private final AccountId owner;
    private ClientDetails client;
    private final Instant startedAt;
    private Instant lastRefreshedAt;
    private Instant expiresAt;
    private RefreshTokenHash currentTokenHash;
    private Instant endedAt;
    private SessionEndReason endReason;
    private final List<RetiredRefreshToken> retiredSinceLoad = new ArrayList<>();

    private UserSession(UserSessionSnapshot state) {
        super(state.version());
        this.id = Objects.requireNonNull(state.id(), "id");
        this.owner = Objects.requireNonNull(state.owner(), "owner");
        this.client = Objects.requireNonNull(state.client(), "client");
        this.startedAt = Objects.requireNonNull(state.startedAt(), "startedAt");
        this.lastRefreshedAt = Objects.requireNonNull(state.lastRefreshedAt(), "lastRefreshedAt");
        this.expiresAt = Objects.requireNonNull(state.expiresAt(), "expiresAt");
        this.currentTokenHash = Objects.requireNonNull(state.currentTokenHash(), "currentTokenHash");
        if ((state.endedAt() == null) != (state.endReason() == null)) {
            throw new IllegalArgumentException("endedAt and endReason must be set together");
        }
        this.endedAt = state.endedAt();
        this.endReason = state.endReason();
    }

    public static UserSession start(SessionId id, AccountId owner, ClientDetails client,
                                    RefreshTokenHash tokenHash, SessionPolicy policy, Instant now) {
        return new UserSession(new UserSessionSnapshot(id, UNSAVED_VERSION, owner, client, now, now,
            now.plus(policy.refreshTokenTtl()), tokenHash, null, null));
    }

    public static UserSession restore(UserSessionSnapshot snapshot) {
        return new UserSession(snapshot);
    }

    /**
     * Exchanges the presented refresh token for {@code newTokenHash}.
     *
     * @param presented     the token the client sent, as located in storage
     * @param ownerStanding current standing of the owning account
     * @return {@link RefreshOutcome#ROTATED} or the reason of rejection; state changes caused by a rejection
     *     (ending the session on reuse) must still be saved by the caller
     */
    public RefreshOutcome refresh(PresentedRefreshToken presented, AccountStanding ownerStanding,
                                  RefreshTokenHash newTokenHash, ClientDetails requestClient,
                                  SessionPolicy policy, Instant now) {
        Objects.requireNonNull(presented, "presented");
        Objects.requireNonNull(ownerStanding, "ownerStanding");
        Objects.requireNonNull(newTokenHash, "newTokenHash");
        Objects.requireNonNull(requestClient, "requestClient");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(now, "now");

        if (isEnded()) {
            return RefreshOutcome.TOKEN_INVALID;
        }
        if (!ownerStanding.acceptsSessionStartedAt(startedAt)) {
            end(SessionEndReason.ACCOUNT_INVALIDATED, now);
            return RefreshOutcome.SESSION_ENDED;
        }
        if (!presented.isCurrent()) {
            return onRetiredTokenPresented(presented, policy, now);
        }
        if (!presented.hash().equals(currentTokenHash) || !expiresAt.isAfter(now)) {
            return RefreshOutcome.TOKEN_INVALID;
        }

        retiredSinceLoad.add(new RetiredRefreshToken(currentTokenHash, now));
        this.currentTokenHash = newTokenHash;
        this.lastRefreshedAt = now;
        this.expiresAt = now.plus(policy.refreshTokenTtl());
        this.client = requestClient;
        return RefreshOutcome.ROTATED;
    }

    private RefreshOutcome onRetiredTokenPresented(PresentedRefreshToken presented, SessionPolicy policy,
                                                   Instant now) {
        if (presented.retiredAt().plus(policy.rotationGracePeriod()).isAfter(now)) {
            // Another tab refreshed a moment ago; the client should retry with the new cookie.
            return RefreshOutcome.CONCURRENT_REFRESH;
        }
        end(SessionEndReason.TOKEN_REUSE_DETECTED, now);
        registerEvent(new RefreshTokenReuseDetected(owner, id, now));
        return RefreshOutcome.TOKEN_INVALID;
    }

    /** Logging out with an already rotated token changes nothing: only the current token ends the session. */
    public void logout(PresentedRefreshToken presented, Instant now) {
        if (presented.isCurrent() && presented.hash().equals(currentTokenHash)) {
            endIfActive(SessionEndReason.LOGOUT, now);
        }
    }

    /** The owner closes one of their sessions. Someone else's session is reported as not found. */
    public void revokeByOwner(AccountId requester, Instant now) {
        if (!owner.equals(requester)) {
            throw new EntityNotFoundException(IdentityErrorKey.SESSION_NOT_FOUND, id.value());
        }
        endIfActive(SessionEndReason.REVOKED_BY_OWNER, now);
    }

    /**
     * @param expectedOwner the account the admin is looking at; a session of another account is reported as
     *                      not found, so an id typed into the wrong account's page cannot end it
     */
    public void revokeByAdmin(AccountId expectedOwner, Instant now) {
        if (!owner.equals(expectedOwner)) {
            throw new EntityNotFoundException(IdentityErrorKey.SESSION_NOT_FOUND, id.value());
        }
        endIfActive(SessionEndReason.REVOKED_BY_ADMIN, now);
    }

    public boolean isActiveAt(Instant now, AccountStanding ownerStanding) {
        return !isEnded() && expiresAt.isAfter(now) && ownerStanding.acceptsSessionStartedAt(startedAt);
    }

    public boolean isEnded() {
        return endedAt != null;
    }

    private void endIfActive(SessionEndReason reason, Instant now) {
        if (!isEnded()) {
            end(reason, now);
        }
    }

    private void end(SessionEndReason reason, Instant now) {
        this.endedAt = now;
        this.endReason = reason;
    }

    // --- read access -----------------------------------------------------------------------------

    /** Tokens retired by rotation since this instance was loaded; the persistence adapter stores them. */
    public List<RetiredRefreshToken> retiredTokensSinceLoad() {
        return List.copyOf(retiredSinceLoad);
    }

    public SessionId id() {
        return id;
    }

    public AccountId owner() {
        return owner;
    }

    public ClientDetails client() {
        return client;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant lastRefreshedAt() {
        return lastRefreshedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public RefreshTokenHash currentTokenHash() {
        return currentTokenHash;
    }

    public Optional<Instant> endedAt() {
        return Optional.ofNullable(endedAt);
    }

    public Optional<SessionEndReason> endReason() {
        return Optional.ofNullable(endReason);
    }
}
