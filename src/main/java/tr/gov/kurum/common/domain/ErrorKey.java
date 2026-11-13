package tr.gov.kurum.common.domain;

/**
 * Machine-readable error identifier. Each bounded context defines its own enum implementing this interface.
 * The user-facing text is resolved by the web layer from {@code messages.properties} using {@link #messageKey()}.
 */
public interface ErrorKey {

    /** Stable code sent to clients, e.g. {@code INVALID_CREDENTIALS}. */
    String code();

    ErrorCategory category();

    default String messageKey() {
        return "error." + code();
    }
}
