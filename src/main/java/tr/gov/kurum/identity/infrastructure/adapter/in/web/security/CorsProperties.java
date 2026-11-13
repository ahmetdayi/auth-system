package tr.gov.kurum.identity.infrastructure.adapter.in.web.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param allowedOrigins exact origins of other sites allowed to call the API with credentials.
 *                       Empty by default: the bundled console is same-origin and needs no CORS at all.
 *                       {@code "*"} is rejected because it cannot be combined with credentials.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("app.cors.allowed-origins must list exact origins, not '*'");
        }
    }
}
