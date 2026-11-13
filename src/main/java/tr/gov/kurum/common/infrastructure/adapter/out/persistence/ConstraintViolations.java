package tr.gov.kurum.common.infrastructure.adapter.out.persistence;

import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/** Identifies which database constraint caused a {@link DataIntegrityViolationException}. */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    public static boolean isViolationOf(DataIntegrityViolationException exception, String constraintName) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName().toLowerCase(Locale.ROOT).contains(constraintName);
            }
            cause = cause.getCause();
        }
        return false;
    }
}
