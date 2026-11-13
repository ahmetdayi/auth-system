package tr.gov.kurum.identity.application.port.out;

import tr.gov.kurum.identity.domain.model.AccessProfile;
import tr.gov.kurum.identity.domain.model.SessionId;

public interface AccessTokenIssuer {

    /** @param sessionId the session the token belongs to, so a device can recognise "this session" */
    IssuedAccessToken issue(AccessProfile profile, SessionId sessionId);
}
