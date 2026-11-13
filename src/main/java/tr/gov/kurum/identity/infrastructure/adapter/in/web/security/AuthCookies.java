package tr.gov.kurum.identity.infrastructure.adapter.in.web.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import tr.gov.kurum.identity.application.port.in.AuthenticationResult;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.TokenProperties;

/**
 * Browser token transport. Both cookies are HttpOnly (page scripts cannot read them), SameSite=Strict and
 * host-only (no Domain attribute, so other subdomains never receive them).
 *
 * <p>The refresh cookie's path is {@code /api/auth}: it reaches refresh and logout, and nothing else.</p>
 */
@Component
public class AuthCookies {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";
    static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final CookieProperties cookieProperties;
    private final TokenProperties tokenProperties;

    AuthCookies(CookieProperties cookieProperties, TokenProperties tokenProperties) {
        this.cookieProperties = cookieProperties;
        this.tokenProperties = tokenProperties;
    }

    public void write(HttpServletResponse response, AuthenticationResult result) {
        add(response, ACCESS_COOKIE, result.accessToken().token(), "/", tokenProperties.accessTokenTtl());
        add(response, REFRESH_COOKIE, result.refreshToken(), REFRESH_COOKIE_PATH, tokenProperties.refreshTokenTtl());
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS_COOKIE, "", "/", Duration.ZERO);
        add(response, REFRESH_COOKIE, "", REFRESH_COOKIE_PATH, Duration.ZERO);
    }

    /** @return the cookie value, or {@code null} when absent or blank */
    public String readAccessToken(HttpServletRequest request) {
        return read(request, ACCESS_COOKIE);
    }

    /** @return the cookie value, or {@code null} when absent or blank */
    public String readRefreshToken(HttpServletRequest request) {
        return read(request, REFRESH_COOKIE);
    }

    private void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cookieProperties.secure())
            .sameSite("Strict")
            .path(path)
            .maxAge(maxAge)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
