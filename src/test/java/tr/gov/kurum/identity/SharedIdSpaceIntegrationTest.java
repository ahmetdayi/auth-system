package tr.gov.kurum.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.support.ApiClient;
import tr.gov.kurum.support.ApiClient.Response;
import tr.gov.kurum.support.IntegrationTestBase;
import tr.gov.kurum.support.TestData.Account;

/**
 * Customers and admins share one id space (the shared-kernel {@code AccountId}), and every context keys its
 * data by it. So each admin operation must check that the id it receives belongs to the kind of account the
 * URL names; otherwise a customer URL could reach an admin.
 */
class SharedIdSpaceIntegrationTest extends IntegrationTestBase {

    @Test
    void customer_urls_never_reach_an_admin_account() {
        Account otherAdmin = data.admin("SUPER");
        ApiClient admin = mobileOf(data.admin("STANDARD", "customer:read", "customer:write"));
        String target = "/api/admin/customers/" + otherAdmin.id();

        assertThat(admin.get(target + "/sessions").code()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(admin.delete(target + "/sessions").code()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(admin.post(target + "/disable").code()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(admin.put(target + "/membership", Map.of("tier", "GOLD")).code()).isEqualTo("MEMBER_NOT_FOUND");
        assertThat(admin.put("/api/customers/" + otherAdmin.id() + "/name",
            Map.of("firstName", "X", "lastName", "Y")).code()).isEqualTo("CUSTOMER_NOT_FOUND");

        // The other admin was not touched.
        assertThat(mobileOf(otherAdmin).get("/api/me").status()).isEqualTo(200);
    }

    @Test
    void admin_urls_never_reach_a_customer_account() {
        Account customer = data.customer();
        ApiClient superAdmin = mobileOf(data.admin("SUPER"));

        assertThat(superAdmin.get("/api/admin/admins/" + customer.id() + "/sessions").code())
            .isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(superAdmin.delete("/api/admin/admins/" + customer.id() + "/sessions").code())
            .isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void lookups_by_id_return_each_contexts_own_data_for_composition() {
        Account gold = data.customerWithMembership("GOLD", null);
        Account plain = data.customer();
        Account otherAdmin = data.admin("STANDARD");
        ApiClient admin = mobileOf(data.admin("STANDARD", "customer:read", "customer:write"));
        admin.post("/api/admin/customers/" + plain.id() + "/disable");
        String ids = "ids=" + gold.id() + "&ids=" + plain.id() + "&ids=" + otherAdmin.id();

        Response accounts = admin.get("/api/admin/accounts?" + ids);
        Response memberships = admin.get("/api/admin/memberships?" + ids);

        assertThat(accounts.status()).isEqualTo(200);
        assertThat(accounts.body().size()).isEqualTo(3);
        assertThat(accounts.body().toString())
            .contains("\"id\":" + plain.id() + ",\"email\":\"" + plain.email() + "\",\"type\":\"CUSTOMER\","
                + "\"status\":\"DISABLED\"");
        assertThat(accounts.body().toString()).doesNotContain("password");
        assertThat(memberships.body().size()).isOne();
        assertThat(memberships.body().get(0).get("memberId").asLong()).isEqualTo(gold.id());
        assertThat(memberships.body().get(0).get("validNow").asBoolean()).isTrue();
    }

    @Test
    void lookups_are_bounded_and_need_customer_read() {
        ApiClient noPermission = mobileOf(data.admin("STANDARD"));
        ApiClient reader = mobileOf(data.admin("STANDARD", "customer:read"));
        StringBuilder tooMany = new StringBuilder("ids=1");
        for (int i = 2; i <= 101; i++) {
            tooMany.append("&ids=").append(i);
        }

        assertThat(noPermission.get("/api/admin/accounts?ids=1").code()).isEqualTo("ACCESS_DENIED");
        assertThat(noPermission.get("/api/admin/memberships?ids=1").code()).isEqualTo("ACCESS_DENIED");
        assertThat(reader.get("/api/admin/accounts?" + tooMany).code()).isEqualTo("VALIDATION_FAILED");
        assertThat(reader.get("/api/admin/memberships?" + tooMany).code()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void the_admin_dashboard_shows_only_the_staff_contexts_data() {
        Account account = data.admin("STANDARD", "customer:read");
        ApiClient admin = mobileOf(account);

        Response dashboard = admin.get("/api/admin/dashboard");

        assertThat(dashboard.status()).isEqualTo(200);
        assertThat(dashboard.body().get("id").asLong()).isEqualTo(account.id());
        assertThat(dashboard.text("fullName")).isEqualTo("Test Admin");
        assertThat(dashboard.body().has("email")).isFalse();         // identity's, from /api/me
        assertThat(dashboard.body().has("permissions")).isFalse();   // identity's, from /api/me
    }
}
