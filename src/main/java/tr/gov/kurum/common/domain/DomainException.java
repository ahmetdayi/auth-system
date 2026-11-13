package tr.gov.kurum.common.domain;

import java.util.Objects;

/** Base type of all expected (business) failures. Carries an {@link ErrorKey} instead of a hard-coded message. */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorKey errorKey;
    private final transient Object[] arguments;

    protected DomainException(ErrorKey errorKey, Object... arguments) {
        super(Objects.requireNonNull(errorKey, "errorKey").code());
        this.errorKey = errorKey;
        this.arguments = arguments.clone();
    }

    protected DomainException(ErrorKey errorKey, Throwable cause, Object... arguments) {
        super(Objects.requireNonNull(errorKey, "errorKey").code(), cause);
        this.errorKey = errorKey;
        this.arguments = arguments.clone();
    }

    public ErrorKey errorKey() {
        return errorKey;
    }

    public Object[] arguments() {
        return arguments.clone();
    }
}
