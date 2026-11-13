package tr.gov.kurum.identity.infrastructure.adapter.in.web;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.common.web.AuthenticatedAccount;
import tr.gov.kurum.common.web.CurrentAccount;
import tr.gov.kurum.identity.application.port.in.SessionManagementUseCase;
import tr.gov.kurum.identity.application.port.in.SessionView;
import tr.gov.kurum.identity.domain.model.SessionId;

/**
 * "My devices". Ending a session stops its refresh token at once; an access token already issued to that
 * device keeps working until it expires (at most the access-token lifetime). Logout-all is immediate.
 */
@RestController
@RequestMapping("/api/sessions")
class SessionController {

    private final SessionManagementUseCase sessions;

    SessionController(SessionManagementUseCase sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    List<SessionView> mySessions(@CurrentAccount AuthenticatedAccount account) {
        return sessions.listActiveSessions(account.accountId(), new SessionId(account.sessionId()));
    }

    /** Another account's session id is answered 404, exactly like an unknown id. */
    @DeleteMapping("/{sessionId}")
    ResponseEntity<Void> revoke(@CurrentAccount AuthenticatedAccount account, @PathVariable long sessionId) {
        sessions.revokeOwnSession(account.accountId(), new SessionId(sessionId));
        return ResponseEntity.noContent().build();
    }
}
