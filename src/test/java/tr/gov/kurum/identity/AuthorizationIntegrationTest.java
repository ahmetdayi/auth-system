package tr.gov.kurum.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.support.ApiClient.Response;
import tr.gov.kurum.support.ApiClient;
import tr.gov.kurum.support.IntegrationTestBase;
import tr.gov.kurum.support.TestData.Account;

class AuthorizationIntegrationTest extends IntegrationTestBase {

    @Test
    void customers_cannot_reach_admin_endpoints_and_admins_are_not_customers() {
        ApiClient customer = mobileOf(data.customer());
        ApiClient admin = mobileOf(data.admin("STANDARD", "customer:read", "customer:write"));

        assertThat(customer.get("/api/admin/dashboard").code()).isEqualTo("ACCESS_DENIED");
        // Admin#n and Customer#n can share the number; the type authority keeps them apart.
        assertThat(admin.get("/api/customer/profile").code()).isEqualTo("ACCESS_DENIED");
        assertThat(admin.get("/api/content/articles").code()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void customers_change_only_their_own_name_admins_need_customer_write() {
        Account owner = data.customer();
        Account other = data.customer();
        ApiClient ownerClient = mobileOf(owner);
        Map<String, String> name = Map.of("firstName", "Yeni", "lastName", "Ad");

        assertThat(ownerClient.put("/api/customers/" + owner.id() + "/name", name).status()).isEqualTo(204);
        assertThat(ownerClient.put("/api/customers/" + other.id() + "/name", name).code()).isEqualTo("ACCESS_DENIED");
        assertThat(mobileOf(data.admin("STANDARD", "customer:write"))
            .put("/api/customers/" + other.id() + "/name", name).status()).isEqualTo(204);
        assertThat(mobileOf(data.admin("STANDARD", "customer:read"))
            .put("/api/customers/" + other.id() + "/name", name).code()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void permissions_separate_reading_from_writing() {
        Account customer = data.customer();
        ApiClient reader = mobileOf(data.admin("STANDARD", "customer:read"));

        assertThat(reader.get("/api/admin/customers?size=5").status()).isEqualTo(200);
        assertThat(reader.post("/api/admin/customers/" + customer.id() + "/disable").code())
            .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void super_admin_passes_role_checks_through_the_hierarchy() {
        Account standard = data.admin("STANDARD");
        ApiClient standardClient = mobileOf(standard);
        ApiClient superAdmin = mobileOf(data.admin("SUPER"));

        assertThat(superAdmin.get("/api/admin/dashboard").status()).isEqualTo(200);   // hasRole('ADMIN')
        assertThat(superAdmin.get("/api/admin/admins/" + standard.id() + "/sessions").status()).isEqualTo(200);
        assertThat(standardClient.get("/api/admin/admins/" + standard.id() + "/sessions").code())
            .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void membership_tiers_follow_the_hierarchy_and_expire() {
        ApiClient gold = mobileOf(data.customerWithMembership("GOLD", null));
        ApiClient silver = mobileOf(data.customerWithMembership("SILVER", null));
        ApiClient expired = mobileOf(data.customerWithMembership("GOLD", clock.instant().minus(Duration.ofDays(1))));

        assertThat(gold.get("/api/content/tiers/SILVER/articles").status()).isEqualTo(200);
        assertThat(gold.get("/api/content/tiers/BRONZE/articles").status()).isEqualTo(200);
        assertThat(silver.get("/api/content/tiers/GOLD/articles").code()).isEqualTo("ACCESS_DENIED");
        assertThat(expired.get("/api/me").body().get("authorities").toString()).doesNotContain("MEMBERSHIP_");
        assertThat(expired.get("/api/content/discount").body().get("discountPercent").decimalValue())
            .isEqualByComparingTo("0");
        assertThat(gold.get("/api/content/discount").body().get("discountPercent").decimalValue())
            .isEqualByComparingTo("20");
    }

    @Test
    void article_rule_in_the_domain_names_the_required_tier() {
        ApiClient silver = mobileOf(data.customerWithMembership("SILVER", null));
        long goldArticle = insertArticle("GOLD");

        Response response = silver.get("/api/content/articles/" + goldArticle);

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.code()).isEqualTo("ARTICLE_REQUIRES_HIGHER_TIER");
        assertThat(response.text("detail")).contains("GOLD");
    }

    @Test
    void permissions_from_roles_are_independent_of_membership() {
        Account premium = data.customer();
        data.assignRole(premium, "PREMIUM_READER", "content:premium:read");

        assertThat(mobileOf(premium).get("/api/content/premium/articles").status()).isEqualTo(200);
        assertThat(mobileOf(data.customerWithMembership("GOLD", null)).get("/api/content/premium/articles").code())
            .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void sort_fields_are_whitelisted_and_page_size_is_bounded() {
        ApiClient admin = mobileOf(data.admin("STANDARD", "customer:read"));

        Response unknownSort = admin.get("/api/admin/customers?sort=password");
        Response tooLarge = admin.get("/api/admin/customers?size=500");
        Response sorted = admin.get("/api/admin/customers?sort=EMAIL&direction=ASC&size=100");

        assertThat(unknownSort.status()).isEqualTo(400);
        assertThat(unknownSort.code()).isEqualTo("MALFORMED_REQUEST");
        assertThat(unknownSort.text("detail")).doesNotContain("Failed to convert");
        assertThat(tooLarge.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(tooLarge.body().get("errors").get(0).get("field").asString()).isEqualTo("size");
        assertThat(sorted.status()).isEqualTo(200);
        assertThat(sorted.body().get("size").asInt()).isEqualTo(100);
    }

    @Test
    void like_wildcards_in_the_email_filter_are_literal() {
        data.customer();
        ApiClient admin = mobileOf(data.admin("STANDARD", "customer:read"));

        Response percent = admin.get("/api/admin/customers?email=%25");

        assertThat(percent.status()).isEqualTo(200);
        assertThat(percent.body().get("totalElements").asLong()).isZero();
    }

    private long insertArticle(String tier) {
        return data.queryLong("""
            INSERT INTO articles (id, title, summary, body, required_tier, published_at, created_at, updated_at, version)
            VALUES (nextval('article_id_seq'), 'Test', 'Özet', 'Gövde', ?, now(), now(), now(), 0)
            RETURNING id
            """, tier);
    }
}
