package tr.gov.kurum.support;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A client on top of MockMvc that behaves like a browser or a mobile app: keeps cookies between requests,
 * sends the XSRF header the way the console's JavaScript does, or sends a Bearer token. The real security
 * filter chain runs; nothing is mocked (no {@code with(csrf())}, no {@code @WithMockUser}).
 */
public final class ApiClient {

    private final MockMvc mvc;
    private final JsonMapper json;
    private final String clientIp;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();
    private String bearerToken;
    private boolean sendCsrfHeader = true;

    public ApiClient(MockMvc mvc, JsonMapper json, String clientIp) {
        this.mvc = mvc;
        this.json = json;
        this.clientIp = clientIp;
    }

    public record Response(int status, JsonNode body, MockHttpServletResponse raw) {

        /** The Problem Details {@code code}, or {@code null}. */
        public String code() {
            return body != null && body.has("code") ? body.get("code").asString() : null;
        }

        public String text(String field) {
            return body.get(field).asString();
        }
    }

    public ApiClient bearer(String token) {
        this.bearerToken = token;
        return this;
    }

    public ApiClient withoutCsrfHeader() {
        this.sendCsrfHeader = false;
        return this;
    }

    public Cookie cookie(String name) {
        return cookies.get(name);
    }

    /** Adds a cookie as if the browser held it (e.g. a cross-site request carrying the victim's cookies). */
    public ApiClient withCookie(Cookie cookie) {
        cookies.put(cookie.getName(), cookie);
        return this;
    }

    public void dropCookie(String name) {
        cookies.remove(name);
    }

    public Response get(String url) {
        return send(HttpMethod.GET, url, null);
    }

    public Response post(String url, Object body) {
        return send(HttpMethod.POST, url, body);
    }

    public Response post(String url) {
        return send(HttpMethod.POST, url, null);
    }

    public Response put(String url, Object body) {
        return send(HttpMethod.PUT, url, body);
    }

    public Response delete(String url) {
        return send(HttpMethod.DELETE, url, null);
    }

    /** Logs in through the browser endpoint; cookies are kept. */
    public Response browserLogin(String email, String password, String userType) {
        return post("/api/auth/login", Map.of("email", email, "password", password, "userType", userType));
    }

    /** Logs in through the mobile endpoint and uses the returned access token from now on. */
    public Response mobileLogin(String email, String password, String userType) {
        Response response = post("/api/auth/mobile/login",
            Map.of("email", email, "password", password, "userType", userType));
        if (response.status() == 200) {
            bearer(response.text("accessToken"));
        }
        return response;
    }

    private Response send(HttpMethod method, String url, Object body) {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, url)
            .accept(MediaType.APPLICATION_JSON)
            .with(r -> {
                r.setRemoteAddr(clientIp);
                return r;
            });
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        if (bearerToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        if (!cookies.isEmpty()) {
            request.cookie(cookies.values().toArray(Cookie[]::new));
        }
        Cookie xsrf = cookies.get("XSRF-TOKEN");
        if (sendCsrfHeader && xsrf != null && method != HttpMethod.GET) {
            request.header("X-XSRF-TOKEN", xsrf.getValue());
        }
        try {
            MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
            for (Cookie cookie : response.getCookies()) {
                if (cookie.getMaxAge() == 0) {
                    cookies.remove(cookie.getName());
                } else {
                    cookies.put(cookie.getName(), cookie);
                }
            }
            String content = response.getContentAsString();
            JsonNode node = content.isBlank() ? null : json.readTree(content);
            return new Response(response.getStatus(), node, response);
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + url + " failed", e);
        }
    }
}
