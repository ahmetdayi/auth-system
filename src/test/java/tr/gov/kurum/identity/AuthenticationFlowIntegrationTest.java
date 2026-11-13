package tr.gov.kurum.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.support.ApiClient.Response;
import tr.gov.kurum.support.ApiClient;
import tr.gov.kurum.support.IntegrationTestBase;
import tr.gov.kurum.support.TestData.Account;
import tr.gov.kurum.support.TestData;

class AuthenticationFlowIntegrationTest extends IntegrationTestBase {

    @Test
    void browser_login_puts_tokens_only_into_http_only_cookies() {
        Account customer = data.customer();
        ApiClient browser = client();

        Response login = browser.browserLogin(customer.email(), TestData.PASSWORD, "CUSTOMER");

        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body().has("accessToken")).isFalse();
        assertThat(login.body().has("refreshToken")).isFalse();
        Cookie access = browser.cookie("access_token");
        Cookie refresh = browser.cookie("refresh_token");
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getSecure()).isTrue();
        assertThat(access.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getPath()).isEqualTo("/api/auth");
        assertThat(browser.cookie("XSRF-TOKEN").isHttpOnly()).isFalse();
        assertThat(browser.get("/api/me").text("email")).isEqualTo(customer.email());
    }

    @Test
    void every_login_failure_gets_the_same_answer() {
        Account customer = data.customer();
        Account disabled = data.customer();
        disable(disabled);
        ApiClient client = client();

        assertThat(client.mobileLogin(customer.email(), "yanlis-sifre-123", "CUSTOMER").code())
            .isEqualTo("INVALID_CREDENTIALS");
        assertThat(client.mobileLogin(TestData.uniqueEmail("nobody"), TestData.PASSWORD, "CUSTOMER").code())
            .isEqualTo("INVALID_CREDENTIALS");
        assertThat(client.mobileLogin(disabled.email(), TestData.PASSWORD, "CUSTOMER").code())
            .isEqualTo("INVALID_CREDENTIALS");
        assertThat(client.mobileLogin(customer.email(), TestData.PASSWORD, "ADMIN").code())
            .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void five_failures_block_even_the_correct_password_but_only_for_that_ip() {
        Account customer = data.customer();
        ApiClient attacker = client();
        for (int i = 0; i < 5; i++) {
            assertThat(attacker.mobileLogin(customer.email(), "yanlis-sifre-123", "CUSTOMER").status()).isEqualTo(401);
        }

        Response blocked = attacker.mobileLogin(customer.email(), TestData.PASSWORD, "CUSTOMER");
        Response otherDevice = client().mobileLogin(customer.email(), TestData.PASSWORD, "CUSTOMER");

        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.code()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        assertThat(otherDevice.status()).isEqualTo(200);
    }

    @Test
    void expired_access_token_is_reported_and_a_refresh_restores_access() {
        ApiClient browser = browserOf(data.customer());
        Cookie oldAccessCookie = browser.cookie("access_token");

        clock.advance(Duration.ofMinutes(16));
        Response expired = browser.get("/api/me");
        Response refreshed = browser.post("/api/auth/refresh");

        assertThat(expired.status()).isEqualTo(401);
        assertThat(expired.code()).isEqualTo("TOKEN_EXPIRED");
        assertThat(expired.raw().getHeader("WWW-Authenticate")).contains("invalid_token");
        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(browser.cookie("access_token").getValue()).isNotEqualTo(oldAccessCookie.getValue());
        assertThat(browser.get("/api/me").status()).isEqualTo(200);
    }

    @Test
    void refresh_rotation_tolerates_a_parallel_tab_but_detects_reuse_later() {
        ApiClient mobile = client();
        Account customer = data.customer();
        Response login = mobile.mobileLogin(customer.email(), TestData.PASSWORD, "CUSTOMER");
        String first = login.text("refreshToken");

        Response rotated = mobile.post("/api/auth/mobile/refresh", Map.of("refreshToken", first));
        String second = rotated.text("refreshToken");
        String latestAccess = rotated.text("accessToken");
        Response parallel = mobile.post("/api/auth/mobile/refresh", Map.of("refreshToken", first));

        assertThat(rotated.status()).isEqualTo(200);
        assertThat(second).isNotEqualTo(first);
        assertThat(parallel.status()).isEqualTo(409);
        assertThat(parallel.code()).isEqualTo("REFRESH_IN_PROGRESS");

        clock.advance(Duration.ofSeconds(6));   // past the 5 s grace period
        Response reuse = mobile.post("/api/auth/mobile/refresh", Map.of("refreshToken", first));
        Response legitimateHolder = mobile.post("/api/auth/mobile/refresh", Map.of("refreshToken", second));
        Response accessAfterReuse = mobile.bearer(latestAccess).get("/api/me");

        assertThat(reuse.code()).isEqualTo("REFRESH_TOKEN_INVALID");
        assertThat(legitimateHolder.code()).isEqualTo("REFRESH_TOKEN_INVALID");
        assertThat(accessAfterReuse.code()).isEqualTo("TOKEN_REVOKED");
    }

    @Test
    void refresh_token_is_not_an_access_token_and_a_forged_payload_is_rejected() {
        Account customer = data.customer();
        Response login = client().mobileLogin(customer.email(), TestData.PASSWORD, "CUSTOMER");

        Response withRefreshToken = client().bearer(login.text("refreshToken")).get("/api/me");
        Response forged = client().bearer(withAuthorityReplaced(login.text("accessToken"),
            "TYPE_CUSTOMER", "TYPE_ADMIN")).get("/api/admin/dashboard");

        assertThat(withRefreshToken.code()).isEqualTo("TOKEN_INVALID");
        assertThat(forged.status()).isEqualTo(401);
        assertThat(forged.code()).isEqualTo("TOKEN_INVALID");
    }

    @Test
    void refresh_token_expires_after_its_lifetime() {
        Account customer = data.customer();
        Response login = client().mobileLogin(customer.email(), TestData.PASSWORD, "CUSTOMER");

        clock.advance(Duration.ofDays(15));

        assertThat(client().post("/api/auth/mobile/refresh", Map.of("refreshToken", login.text("refreshToken")))
            .code()).isEqualTo("REFRESH_TOKEN_INVALID");
    }

    @Test
    void browser_logout_requires_csrf_and_revokes_both_tokens() {
        ApiClient browser = browserOf(data.customer());
        Cookie access = browser.cookie("access_token");
        Cookie refresh = browser.cookie("refresh_token");

        // A cross-site request carries the cookies but cannot read XSRF-TOKEN to copy it into the header.
        Response crossSite = client().withCookie(access).withCookie(refresh).post("/api/auth/logout");
        Response logout = browser.post("/api/auth/logout");

        assertThat(crossSite.status()).isEqualTo(403);
        assertThat(crossSite.code()).isEqualTo("CSRF_TOKEN_INVALID");
        assertThat(logout.status()).isEqualTo(204);
        assertThat(browser.cookie("access_token")).isNull();
        assertThat(browser.cookie("refresh_token")).isNull();
        assertThat(client().bearer(access.getValue()).get("/api/me").code()).isEqualTo("TOKEN_REVOKED");
        assertThat(client().withCookie(refresh).post("/api/auth/refresh").code())
            .isEqualTo("REFRESH_TOKEN_INVALID");
    }

    @Test
    void requests_with_an_authorization_header_need_no_csrf_token() {
        ApiClient mobile = mobileOf(data.customer());

        Response logoutAll = mobile.withoutCsrfHeader().post("/api/auth/logout-all");

        assertThat(logoutAll.status()).isEqualTo(204);
    }

    @Test
    void logout_everywhere_revokes_every_session_and_access_token_of_the_account() {
        Account customer = data.customer();
        ApiClient laptop = browserOf(customer);
        ApiClient phone = client();
        Response phoneLogin = phone.mobileLogin(customer.email(), TestData.PASSWORD, "CUSTOMER");

        assertThat(laptop.post("/api/auth/logout-all").status()).isEqualTo(204);

        assertThat(phone.get("/api/me").code()).isEqualTo("TOKEN_REVOKED");
        assertThat(client().post("/api/auth/mobile/refresh",
            Map.of("refreshToken", phoneLogin.text("refreshToken"))).code()).isEqualTo("SESSION_ENDED");
        // A new login after the cutoff works normally.
        clock.advance(Duration.ofMillis(1));
        ApiClient newSession = client();
        assertThat(newSession.mobileLogin(customer.email(), TestData.PASSWORD, "CUSTOMER").status()).isEqualTo(200);
        assertThat(newSession.get("/api/me").status()).isEqualTo(200);
    }

    @Test
    void anonymous_request_to_a_protected_endpoint_is_401_with_a_reason() {
        Response response = client().get("/api/sessions");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.code()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(response.raw().getContentType()).startsWith("application/problem+json");
    }

    private void disable(Account account) {
        ApiClient admin = mobileOf(data.admin("STANDARD", "customer:read", "customer:write"));
        assertThat(admin.post("/api/admin/customers/" + account.id() + "/disable").status()).isEqualTo(204);
    }

    /** Changes the payload and keeps the original signature, as an attacker without the key would. */
    private static String withAuthorityReplaced(String jwt, String from, String to) {
        String[] parts = jwt.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forged = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.replace("\"" + from + "\"", "\"" + to + "\"").getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + forged + "." + parts[2];
    }
}
