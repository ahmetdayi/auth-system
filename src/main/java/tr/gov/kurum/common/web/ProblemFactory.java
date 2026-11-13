package tr.gov.kurum.common.web;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.ErrorCategory;
import tr.gov.kurum.common.domain.ErrorKey;

/**
 * Builds RFC 7807 problem details. Every error response carries a machine-readable {@code code}; the
 * {@code detail} text comes from {@code messages.properties}. Internal details never reach the client.
 */
@Component
@RequiredArgsConstructor
public class ProblemFactory {

    private final MessageSource messageSource;

    public static HttpStatus statusOf(ErrorCategory category) {
        return switch (category) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public ProblemDetail create(ErrorKey errorKey, Object[] arguments, String requestPath) {
        HttpStatus status = statusOf(errorKey.category());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message(errorKey, arguments));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", errorKey.code());
        if (requestPath != null) {
            problem.setInstance(URI.create(requestPath));
        }
        return problem;
    }

    public String message(ErrorKey errorKey, Object[] arguments) {
        return messageSource.getMessage(errorKey.messageKey(), arguments, errorKey.code(),
            LocaleContextHolder.getLocale());
    }
}
