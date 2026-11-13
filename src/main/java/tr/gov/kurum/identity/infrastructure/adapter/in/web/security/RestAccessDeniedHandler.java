package tr.gov.kurum.identity.infrastructure.adapter.in.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.CommonErrorKey;
import tr.gov.kurum.common.web.ProblemResponseWriter;

/** 403 for an authenticated caller without the required authority, and for a missing or wrong CSRF token. */
@Component
class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ProblemResponseWriter problemWriter;

    RestAccessDeniedHandler(ProblemResponseWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            log.debug("CSRF token missing or invalid: method={}, path={}", request.getMethod(), request.getRequestURI());
            problemWriter.write(request, response, SecurityErrorKey.CSRF_TOKEN_INVALID);
            return;
        }
        log.debug("Access denied: path={}", request.getRequestURI());
        problemWriter.write(request, response, CommonErrorKey.ACCESS_DENIED);
    }
}
