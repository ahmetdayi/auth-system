package tr.gov.kurum.identity.infrastructure.adapter.in.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.web.ProblemResponseWriter;

/**
 * 401 for an anonymous request to a protected endpoint. When the filter rejected a token it left the reason
 * in a request attribute; the client uses it to decide between a silent refresh (TOKEN_EXPIRED,
 * TOKEN_REVOKED) and the login page.
 */
@Component
class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter problemWriter;

    RestAuthenticationEntryPoint(ProblemResponseWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        SecurityErrorKey reason = request.getAttribute(JwtAuthenticationFilter.TOKEN_FAILURE_ATTRIBUTE)
            instanceof SecurityErrorKey failure ? failure : SecurityErrorKey.AUTHENTICATION_REQUIRED;
        // RFC 6750: a token that was presented but rejected is reported as invalid_token.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, reason == SecurityErrorKey.AUTHENTICATION_REQUIRED
            ? "Bearer" : "Bearer error=\"invalid_token\"");
        problemWriter.write(request, response, reason);
    }
}
