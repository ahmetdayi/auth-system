package tr.gov.kurum.identity.infrastructure.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import tr.gov.kurum.identity.domain.model.ClientDetails;

/**
 * Device information for the session list and the login throttle.
 *
 * <p>The IP is {@code getRemoteAddr()}. Behind a reverse proxy, set {@code server.forward-headers-strategy}
 * so that it reflects the real client; never read {@code X-Forwarded-For} directly, the client can forge it
 * and would then choose its own throttle key.</p>
 */
final class ClientDetailsResolver {

    private ClientDetailsResolver() {
    }

    static ClientDetails from(HttpServletRequest request) {
        return new ClientDetails(request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
    }
}
