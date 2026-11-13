package tr.gov.kurum.common.domain;

/** The requested aggregate does not exist (or must not be revealed to the caller). */
public class EntityNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public EntityNotFoundException(ErrorKey errorKey, Object... arguments) {
        super(errorKey, arguments);
    }
}
