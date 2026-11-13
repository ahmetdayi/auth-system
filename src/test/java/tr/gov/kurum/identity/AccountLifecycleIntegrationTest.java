package tr.gov.kurum.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.support.ApiClient.Response;
import tr.gov.kurum.support.ApiClient;
import tr.gov.kurum.support.IntegrationTestBase;
import tr.gov.kurum.support.TestData.Account;
import tr.gov.kurum.support.TestData;

class AccountLifecycleIntegrationTest extends IntegrationTestBase {

    @Test
    void registration_normalises_the_email_and_rejects_duplicates_in_any_case() {
        ApiClient browser = client();
        browser.get("/api/me");   // any request hands out the XSRF-TOKEN cookie
        String email = TestData.uniqueEmail("ILKER").toUpperCase(Locale.ROOT);
        Map<String, String> body = Map.of("email", email, "password", "Gecerli-Sifre-1",
            "firstName", "İlker", "lastName", "Işık");

        Response created = browser.post("/api/auth/register", body);
        Response duplicate = browser.post("/api/auth/register", Map.of("email", email.toLowerCase(Locale.ROOT),
            "password", "Gecerli-Sifre-1", "firstName", "A", "lastName", "B"));
        Response login = client().mobileLogin(email.toLowerCase(Locale.ROOT), "Gecerli-Sifre-1", "CUSTOMER");

        assertThat(created.status()).isEqualTo(201);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.code()).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(login.status()).isEqualTo(200);
    }

    @Test
    void registration_needs_csrf_and_validates_passwords_in_bytes() {
        ApiClient browser = client();
        browser.get("/api/me");

        Response withoutCsrf = client().post("/api/auth/register", Map.of("email", TestData.uniqueEmail("a"),
            "password", "Gecerli-Sifre-1", "firstName", "A", "lastName", "B"));
        Response tooLong = browser.post("/api/auth/register", Map.of("email", TestData.uniqueEmail("b"),
            "password", "ğ".repeat(37), "firstName", "A", "lastName", "B"));
        Response blank = browser.post("/api/auth/register", Map.of("email", "", "password", "",
            "firstName", "", "lastName", ""));

        assertThat(withoutCsrf.code()).isEqualTo("CSRF_TOKEN_INVALID");
        assertThat(tooLong.code()).isEqualTo("PASSWORD_TOO_LONG");
        assertThat(blank.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(blank.body().get("errors").size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void ban_stops_tokens_refresh_and_login_at_once_and_unban_restores_login() {
        Account customer = data.customer();
        ApiClient victim = client();
        Response session = victim.mobileLogin(customer.email(), TestData.PASSWORD, "CUSTOMER");
        ApiClient admin = mobileOf(data.admin("STANDARD", "customer:read", "customer:write"));

        assertThat(admin.post("/api/admin/customers/" + customer.id() + "/disable").status()).isEqualTo(204);

        assertThat(victim.get("/api/me").code()).isEqualTo("TOKEN_REVOKED");
        assertThat(client().post("/api/auth/mobile/refresh", Map.of("refreshToken", session.text("refreshToken")))
            .code()).isEqualTo("SESSION_ENDED");
        assertThat(client().mobileLogin(customer.email(), TestData.PASSWORD, "CUSTOMER").code())
            .isEqualTo("INVALID_CREDENTIALS");

        assertThat(admin.post("/api/admin/customers/" + customer.id() + "/enable").status()).isEqualTo(204);
        clock.advance(Duration.ofMillis(1));
        ApiClient afterUnban = client();
        assertThat(afterUnban.mobileLogin(customer.email(), TestData.PASSWORD, "CUSTOMER").status()).isEqualTo(200);
        assertThat(afterUnban.get("/api/me").status()).isEqualTo(200);
    }

    @Test
    void password_change_ends_all_sessions_and_the_old_password() {
        Account customer = data.customer();
        ApiClient browser = browserOf(customer);
        ApiClient phone = mobileOf(customer);

        Response wrongCurrent = browser.post("/api/account/password",
            Map.of("currentPassword", "yanlis-sifre-123", "newPassword", "Yeni-Sifre-2026"));
        Response change = browser.post("/api/account/password",
            Map.of("currentPassword", TestData.PASSWORD, "newPassword", "Yeni-Sifre-2026"));

        assertThat(wrongCurrent.code()).isEqualTo("WRONG_CURRENT_PASSWORD");
        assertThat(change.status()).isEqualTo(204);
        assertThat(browser.cookie("access_token")).isNull();
        assertThat(phone.get("/api/me").code()).isEqualTo("TOKEN_REVOKED");
        assertThat(client().mobileLogin(customer.email(), TestData.PASSWORD, "CUSTOMER").code())
            .isEqualTo("INVALID_CREDENTIALS");
        clock.advance(Duration.ofMillis(1));
        ApiClient relogin = client();
        assertThat(relogin.mobileLogin(customer.email(), "Yeni-Sifre-2026", "CUSTOMER").status()).isEqualTo(200);
        assertThat(relogin.get("/api/me").status()).isEqualTo(200);
    }

    @Test
    void granted_membership_reaches_the_token_at_the_next_refresh() {
        Account customer = data.customer();
        ApiClient browser = browserOf(customer);
        ApiClient admin = mobileOf(data.admin("STANDARD", "customer:read", "customer:write"));

        assertThat(admin.put("/api/admin/customers/" + customer.id() + "/membership", Map.of("tier", "SILVER"))
            .status()).isEqualTo(204);
        assertThat(browser.get("/api/me").body().get("authorities").toString()).doesNotContain("MEMBERSHIP_SILVER");

        assertThat(browser.post("/api/auth/refresh").status()).isEqualTo(200);
        assertThat(browser.get("/api/me").body().get("authorities").toString()).contains("MEMBERSHIP_SILVER");
    }

    @Test
    void addresses_belong_to_the_customer_aggregate_and_are_limited() {
        ApiClient browser = browserOf(data.customer());
        for (int i = 1; i <= 5; i++) {
            assertThat(browser.post("/api/customer/addresses",
                Map.of("label", "Adres " + i, "city", "Ankara", "line", "Sokak " + i)).status()).isEqualTo(201);
        }

        Response sixth = browser.post("/api/customer/addresses",
            Map.of("label", "Fazla", "city", "Ankara", "line", "Sokak"));
        Response profile = browser.get("/api/customer/profile");
        long firstAddress = profile.body().get("addresses").get(0).get("id").asLong();

        assertThat(sixth.status()).isEqualTo(422);
        assertThat(sixth.code()).isEqualTo("ADDRESS_LIMIT_REACHED");
        assertThat(browser.delete("/api/customer/addresses/" + firstAddress).status()).isEqualTo(204);
        assertThat(browser.get("/api/customer/profile").body().get("addresses").size()).isEqualTo(4);
    }

    @Test
    void users_see_and_end_only_their_own_sessions() {
        Account owner = data.customer();
        ApiClient laptop = browserOf(owner);
        ApiClient phone = mobileOf(owner);
        ApiClient stranger = mobileOf(data.customer());

        Response sessions = laptop.get("/api/sessions");
        long phoneSession = phone.get("/api/me").body().get("sessionId").asLong();

        assertThat(sessions.body().size()).isEqualTo(2);
        assertThat(sessions.body().toString()).contains("\"current\":true");
        assertThat(stranger.delete("/api/sessions/" + phoneSession).code()).isEqualTo("SESSION_NOT_FOUND");
        assertThat(laptop.delete("/api/sessions/" + phoneSession).status()).isEqualTo(204);
        assertThat(laptop.get("/api/sessions").body().size()).isEqualTo(1);
    }
}
