package tr.gov.kurum.common.domain;

/** A business rule or input invariant was violated. The HTTP status follows {@link ErrorKey#category()}. */
public class BusinessRuleException extends DomainException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(ErrorKey errorKey, Object... arguments) {
        super(errorKey, arguments);
    }

    public BusinessRuleException(ErrorKey errorKey, Throwable cause, Object... arguments) {
        super(errorKey, cause, arguments);
    }
}
