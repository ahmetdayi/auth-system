package tr.gov.kurum.identity.infrastructure.adapter.in.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secure     {@code Secure} attribute; true by default, turned off only for plain-HTTP local runs
 * @param csrfDomain domain of the XSRF-TOKEN cookie when the UI and the API live on different subdomains;
 *                   {@code null} keeps it host-only. Auth cookies are always host-only.
 */
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(@DefaultValue("true") boolean secure, String csrfDomain) {
}
