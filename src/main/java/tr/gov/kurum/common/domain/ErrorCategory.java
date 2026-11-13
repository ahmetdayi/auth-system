package tr.gov.kurum.common.domain;

/**
 * Technology-neutral classification of a failure. The web adapter maps each category to an HTTP status;
 * the domain never knows about HTTP.
 */
public enum ErrorCategory {
    INVALID_INPUT,
    UNAUTHENTICATED,
    ACCESS_DENIED,
    NOT_FOUND,
    CONFLICT,
    RULE_VIOLATION,
    RATE_LIMITED,
    UNAVAILABLE,
    INTERNAL
}
