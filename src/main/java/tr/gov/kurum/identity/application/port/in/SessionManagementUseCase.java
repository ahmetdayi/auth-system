package tr.gov.kurum.identity.application.port.in;

import java.util.List;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.identity.domain.model.SessionId;

/** An account manages its own sessions ("my devices"). */
public interface SessionManagementUseCase {

    /**
     * @param currentSession the session of the caller's access token, marked as "this device";
     *                       may be {@code null}
     */
    List<SessionView> listActiveSessions(AccountId owner, SessionId currentSession);

    void revokeOwnSession(AccountId owner, SessionId sessionId);
}
