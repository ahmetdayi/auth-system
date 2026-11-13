package tr.gov.kurum.identity.infrastructure.adapter.in.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/**
 * The handler Spring Security recommends for JavaScript clients. The script copies the raw value of the
 * {@code XSRF-TOKEN} cookie into the {@code X-XSRF-TOKEN} header, so a header value is resolved as plain;
 * a value rendered into a server-side form is masked (BREACH protection) and resolved with XOR.
 * Loading the token on every request also makes the repository write the cookie.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.xor.handle(request, response, csrfToken);
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (headerValue != null && !headerValue.isBlank() ? this.plain : this.xor)
            .resolveCsrfTokenValue(request, csrfToken);
    }
}
