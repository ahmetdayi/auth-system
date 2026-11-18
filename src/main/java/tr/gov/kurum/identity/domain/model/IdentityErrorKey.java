package tr.gov.kurum.identity.domain.model;

import tr.gov.kurum.common.domain.ErrorCategory;
import tr.gov.kurum.common.domain.ErrorKey;

/** Error codes of the identity bounded context. Codes are part of the API contract — do not rename. */
public enum IdentityErrorKey implements ErrorKey {

    PASSWORD_REQUIRED(ErrorCategory.INVALID_INPUT),
    PASSWORD_TOO_SHORT(ErrorCategory.INVALID_INPUT),
    PASSWORD_TOO_LONG(ErrorCategory.INVALID_INPUT),
    INVALID_ROLE_NAME(ErrorCategory.RULE_VIOLATION),
    INVALID_PERMISSION_NAME(ErrorCategory.RULE_VIOLATION),

    INVALID_CREDENTIALS(ErrorCategory.UNAUTHENTICATED),
    TOO_MANY_LOGIN_ATTEMPTS(ErrorCategory.RATE_LIMITED),
    EMAIL_ALREADY_REGISTERED(ErrorCategory.CONFLICT),
    WRONG_CURRENT_PASSWORD(ErrorCategory.RULE_VIOLATION),

    REFRESH_TOKEN_INVALID(ErrorCategory.UNAUTHENTICATED),
    REFRESH_IN_PROGRESS(ErrorCategory.CONFLICT),
    SESSION_ENDED(ErrorCategory.UNAUTHENTICATED),
    SESSION_NOT_FOUND(ErrorCategory.NOT_FOUND),

    ACCOUNT_NOT_FOUND(ErrorCategory.NOT_FOUND),
    INVALID_MEMBERSHIP_LEVEL(ErrorCategory.INVALID_INPUT),

    SUPPORT_GRANT_NOT_FOUND(ErrorCategory.NOT_FOUND),
    SUPPORT_GRANT_CONFLICT(ErrorCategory.CONFLICT),
    SUPPORT_GRANT_INVALID_STATE(ErrorCategory.RULE_VIOLATION),
    SUPPORT_EXTENSION_LIMIT(ErrorCategory.RULE_VIOLATION),
    SUPPORT_SCOPE_INVALID(ErrorCategory.INVALID_INPUT);

    private final ErrorCategory category;

    IdentityErrorKey(ErrorCategory category) {
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
