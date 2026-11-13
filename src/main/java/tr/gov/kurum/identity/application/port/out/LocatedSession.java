package tr.gov.kurum.identity.application.port.out;

import java.util.Objects;
import tr.gov.kurum.identity.domain.model.PresentedRefreshToken;
import tr.gov.kurum.identity.domain.model.UserSession;

/** A session found through a presented refresh token, together with what that token turned out to be. */
public record LocatedSession(UserSession session, PresentedRefreshToken presentedToken) {

    public LocatedSession {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(presentedToken, "presentedToken");
    }
}
