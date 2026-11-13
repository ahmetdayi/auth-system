package tr.gov.kurum.staff.domain.model;

import tr.gov.kurum.common.domain.ErrorCategory;
import tr.gov.kurum.common.domain.ErrorKey;

/** Error codes of the staff bounded context. Codes are part of the API contract — do not rename. */
public enum StaffErrorKey implements ErrorKey {

    INVALID_ADMIN_PROFILE(ErrorCategory.INVALID_INPUT),
    ADMIN_PROFILE_NOT_FOUND(ErrorCategory.NOT_FOUND);

    private final ErrorCategory category;

    StaffErrorKey(ErrorCategory category) {
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
