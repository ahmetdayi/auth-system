package tr.gov.kurum.identity.domain.model;

import tr.gov.kurum.common.domain.BusinessRuleException;

/** Result of {@link UserSession#refresh}. Rejections carry the error to report. */
public enum RefreshOutcome {

    ROTATED(null),
    CONCURRENT_REFRESH(IdentityErrorKey.REFRESH_IN_PROGRESS),
    TOKEN_INVALID(IdentityErrorKey.REFRESH_TOKEN_INVALID),
    SESSION_ENDED(IdentityErrorKey.SESSION_ENDED);

    private final IdentityErrorKey rejection;

    RefreshOutcome(IdentityErrorKey rejection) {
        this.rejection = rejection;
    }

    public void requireRotated() {
        if (rejection != null) {
            throw new BusinessRuleException(rejection);
        }
    }
}
