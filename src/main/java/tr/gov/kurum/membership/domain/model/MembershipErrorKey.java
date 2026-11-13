package tr.gov.kurum.membership.domain.model;

import tr.gov.kurum.common.domain.ErrorCategory;
import tr.gov.kurum.common.domain.ErrorKey;

/** Error codes of the membership bounded context. Codes are part of the API contract — do not rename. */
public enum MembershipErrorKey implements ErrorKey {

    INVALID_MEMBERSHIP_PERIOD(ErrorCategory.INVALID_INPUT),
    MEMBER_NOT_FOUND(ErrorCategory.NOT_FOUND),
    MEMBERSHIP_NOT_FOUND(ErrorCategory.NOT_FOUND);

    private final ErrorCategory category;

    MembershipErrorKey(ErrorCategory category) {
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
