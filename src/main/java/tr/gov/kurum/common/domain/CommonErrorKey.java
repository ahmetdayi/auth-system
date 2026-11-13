package tr.gov.kurum.common.domain;

/** Error codes shared by every bounded context. */
public enum CommonErrorKey implements ErrorKey {

    VALIDATION_FAILED(ErrorCategory.INVALID_INPUT),
    INVALID_IDENTIFIER(ErrorCategory.INVALID_INPUT),
    INVALID_EMAIL(ErrorCategory.INVALID_INPUT),
    MALFORMED_REQUEST(ErrorCategory.INVALID_INPUT),
    ACCESS_DENIED(ErrorCategory.ACCESS_DENIED),
    CONCURRENT_UPDATE(ErrorCategory.CONFLICT),
    SERVICE_UNAVAILABLE(ErrorCategory.UNAVAILABLE),
    INTERNAL_ERROR(ErrorCategory.INTERNAL);

    private final ErrorCategory category;

    CommonErrorKey(ErrorCategory category) {
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
