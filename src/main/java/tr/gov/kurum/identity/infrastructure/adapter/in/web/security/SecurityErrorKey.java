package tr.gov.kurum.identity.infrastructure.adapter.in.web.security;

import tr.gov.kurum.common.domain.ErrorCategory;
import tr.gov.kurum.common.domain.ErrorKey;

/** Errors produced by the security filter chain, before a controller is reached. */
public enum SecurityErrorKey implements ErrorKey {

    AUTHENTICATION_REQUIRED(ErrorCategory.UNAUTHENTICATED),
    TOKEN_EXPIRED(ErrorCategory.UNAUTHENTICATED),
    TOKEN_INVALID(ErrorCategory.UNAUTHENTICATED),
    TOKEN_REVOKED(ErrorCategory.UNAUTHENTICATED),
    REFRESH_TOKEN_MISSING(ErrorCategory.UNAUTHENTICATED),
    CSRF_TOKEN_INVALID(ErrorCategory.ACCESS_DENIED),
    AUTH_UNAVAILABLE(ErrorCategory.UNAVAILABLE);

    private final ErrorCategory category;

    SecurityErrorKey(ErrorCategory category) {
        this.category = category;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
