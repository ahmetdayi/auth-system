package tr.gov.kurum.identity.application.port.in;

import java.time.Instant;
import tr.gov.kurum.identity.domain.model.SessionId;
import tr.gov.kurum.identity.domain.model.UserSession;

public record SessionView(
        long id,
        String userAgent,
        String ipAddress,
        Instant startedAt,
        Instant lastRefreshedAt,
        Instant expiresAt,
        boolean current) {

    public static SessionView of(UserSession session, SessionId currentSession) {
        return new SessionView(
            session.id().value(),
            session.client().userAgent(),
            session.client().ipAddress(),
            session.startedAt(),
            session.lastRefreshedAt(),
            session.expiresAt(),
            session.id().equals(currentSession));
    }
}
