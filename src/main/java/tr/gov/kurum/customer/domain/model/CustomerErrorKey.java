package tr.gov.kurum.customer.domain.model;

import tr.gov.kurum.common.domain.ErrorCategory;
import tr.gov.kurum.common.domain.ErrorKey;

/** Error codes of the customer bounded context. Codes are part of the API contract — do not rename. */
public enum CustomerErrorKey implements ErrorKey {

    INVALID_PERSON_NAME(ErrorCategory.INVALID_INPUT),
    INVALID_ADDRESS(ErrorCategory.INVALID_INPUT),
    CUSTOMER_NOT_FOUND(ErrorCategory.NOT_FOUND),
    ADDRESS_NOT_FOUND(ErrorCategory.NOT_FOUND),
    ADDRESS_LIMIT_REACHED(ErrorCategory.RULE_VIOLATION);

    private final ErrorCategory category;

    CustomerErrorKey(ErrorCategory category) {
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
