package tr.gov.kurum.identity.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.identity.domain.model.RefreshTokenHash;
import tr.gov.kurum.identity.domain.model.SessionId;
import tr.gov.kurum.identity.domain.model.UserSession;

public interface UserSessionRepository {

    SessionId nextId();

    /** Finds the session whose current <em>or retired</em> refresh token has this hash. */
    Optional<LocatedSession> findByPresentedToken(RefreshTokenHash hash);

    Optional<UserSession> findById(SessionId id);

    /**
     * Stores the session and the tokens it retired since it was loaded. A concurrent change of the same
     * session surfaces as Spring's {@code OptimisticLockingFailureException} at flush/commit.
     */
    void save(UserSession session);

    /** Sessions of the owner that are neither ended nor expired, newest first, at most {@code limit}. */
    List<UserSession> findUnendedByOwner(AccountId owner, Instant now, int limit);

    /** Deletes up to {@code batchSize} sessions that expired before {@code now}. */
    int deleteExpired(Instant now, int batchSize);
}
