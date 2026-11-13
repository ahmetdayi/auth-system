package tr.gov.kurum.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tr.gov.kurum.common.domain.CommonErrorKey;
import tr.gov.kurum.common.domain.DomainException;

/**
 * Single top-level error boundary of the REST API. Expected failures ({@link DomainException}) are mapped by
 * their category and are not logged as errors; unexpected ones are logged once, here, and answered with a
 * generic message.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemFactory problemFactory;

    public GlobalExceptionHandler(ProblemFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(DomainException ex, HttpServletRequest request) {
        log.debug("Request rejected: code={}, path={}", ex.errorKey().code(), request.getRequestURI());
        return problem(problemFactory.create(ex.errorKey(), ex.arguments(), request.getRequestURI()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        log.debug("Concurrent update rejected: path={}", request.getRequestURI());
        return problem(problemFactory.create(CommonErrorKey.CONCURRENT_UPDATE, new Object[0], request.getRequestURI()));
    }

    /** Database or Redis unreachable: a temporary condition, answered with 503 so clients may retry. */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ProblemDetail> handleResourceFailure(DataAccessResourceFailureException ex,
                                                               HttpServletRequest request) {
        log.error("Backing service unavailable: path={}, cause={}", request.getRequestURI(), ex.getMessage());
        return problem(problemFactory.create(CommonErrorKey.SERVICE_UNAVAILABLE, new Object[0],
            request.getRequestURI()));
    }

    /**
     * Last resort. Security exceptions are re-thrown so that Spring Security's ExceptionTranslationFilter can
     * still decide between 401 (anonymous) and 403 (authenticated).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Unhandled exception: method={}, path={}", request.getMethod(), request.getRequestURI(), ex);
        return problem(problemFactory.create(CommonErrorKey.INTERNAL_ERROR, new Object[0], request.getRequestURI()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = problemFactory.create(CommonErrorKey.VALIDATION_FAILED, new Object[0], path(request));
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> Map.of("field", error.getField(),
                "message", Objects.toString(error.getDefaultMessage(), "")))
            .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        ProblemDetail problem = problemFactory.create(CommonErrorKey.VALIDATION_FAILED, new Object[0], path(request));
        // Same shape as body validation errors: one {field, message} per violated constraint.
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
            .flatMap(result -> result.getResolvableErrors().stream()
                .map(error -> Map.of(
                    "field", Objects.toString(result.getMethodParameter().getParameterName(), ""),
                    "message", Objects.toString(error.getDefaultMessage(), ""))))
            .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /** Adds a {@code code} to the problem details Spring MVC creates for its own exceptions (404, 405, 415...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            problem.setProperty("code", codeFor(statusCode));
            if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
                // Spring's text names parameter types and conversion internals; give a neutral message instead.
                problem.setDetail(problemFactory.message(CommonErrorKey.MALFORMED_REQUEST, new Object[0]));
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static String codeFor(HttpStatusCode statusCode) {
        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            return CommonErrorKey.MALFORMED_REQUEST.code();
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? "HTTP_" + statusCode.value() : status.name();
    }

    private static ResponseEntity<ProblemDetail> problem(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : null;
    }
}
