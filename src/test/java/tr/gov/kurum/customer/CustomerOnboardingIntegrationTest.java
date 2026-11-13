package tr.gov.kurum.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.outbox.OutboxRelay;
import tr.gov.kurum.support.ApiClient;
import tr.gov.kurum.support.ApiClient.Response;
import tr.gov.kurum.support.IntegrationTestBase;
import tr.gov.kurum.support.TestData;

/**
 * Registration crosses two bounded contexts: identity commits the account and an outbox row in one
 * transaction; the customer context creates the profile when the relay delivers the event. The relay is
 * switched off in tests, so each test delivers explicitly and can look at the state in between.
 */
class CustomerOnboardingIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "Gecerli-Sifre-1";

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void the_profile_appears_once_the_outbox_delivers_the_registration() {
        String email = TestData.uniqueEmail("kayit");
        long accountId = register(email, "  Ayşe ", "Yılmaz");
        ApiClient customer = client();
        assertThat(customer.mobileLogin(email, PASSWORD, "CUSTOMER").status()).isEqualTo(200);

        // Committed: the account and its event. Not yet: the profile, which belongs to another context.
        assertThat(outboxRowsFor(accountId)).isOne();
        assertThat(customer.get("/api/customer/profile").code()).isEqualTo("CUSTOMER_NOT_FOUND");

        assertThat(relay.deliverDue()).isPositive();

        Response profile = customer.get("/api/customer/profile");
        assertThat(profile.status()).isEqualTo(200);
        assertThat(profile.body().get("id").asLong()).isEqualTo(accountId);
        assertThat(profile.text("contactEmail")).isEqualTo(email);
        assertThat(profile.text("firstName")).isEqualTo("Ayşe");   // trimmed by the customer context
        assertThat(data.queryLong("""
            SELECT count(*) FROM outbox_events
            WHERE payload LIKE ? AND published_at IS NOT NULL AND attempts = 0
            """, "%\"value\":" + accountId + "}%")).isOne();
    }

    @Test
    void a_redelivered_event_does_not_create_a_second_profile_or_overwrite_changes() {
        String email = TestData.uniqueEmail("tekrar");
        long accountId = register(email, "Mehmet", "Kaya");
        relay.deliverDue();
        ApiClient customer = client();
        customer.mobileLogin(email, PASSWORD, "CUSTOMER");
        assertThat(customer.put("/api/customers/" + accountId + "/name",
            Map.of("firstName", "Mehmet Ali", "lastName", "Kaya")).status()).isEqualTo(204);

        // At-least-once delivery: the same event arrives again (e.g. a crash after delivery, before the mark).
        jdbc.update("UPDATE outbox_events SET published_at = NULL WHERE payload LIKE ?",
            "%\"value\":" + accountId + "}%");
        assertThat(relay.deliverDue()).isPositive();

        assertThat(data.queryLong("SELECT count(*) FROM customer_profiles WHERE account_id = ?", accountId)).isOne();
        assertThat(customer.get("/api/customer/profile").text("firstName")).isEqualTo("Mehmet Ali");
    }

    @Test
    void a_failing_event_is_retried_with_back_off_and_does_not_block_the_others() {
        long poison = data.queryLong("""
            INSERT INTO outbox_events (id, event_type, payload, occurred_at, attempts, next_attempt_at,
                                       created_at, updated_at, version)
            VALUES (nextval('outbox_event_id_seq'), 'java.lang.String', '"x"', now(), 0, ?, now(), now(), 0)
            RETURNING id
            """, java.sql.Timestamp.from(clock.instant().minusSeconds(1)));
        try {
            long accountId = register(TestData.uniqueEmail("sonraki"), "Can", "Demir");

            relay.deliverDue();

            // The poison row (only integration events are ever deserialized) failed; the later one went through.
            assertThat(data.queryLong("SELECT attempts FROM outbox_events WHERE id = ?", poison)).isOne();
            assertThat(jdbc.queryForObject("SELECT last_error FROM outbox_events WHERE id = ?", String.class,
                poison)).contains("Not an integration event");
            assertThat(data.queryLong("SELECT count(*) FROM customer_profiles WHERE account_id = ?", accountId))
                .isOne();

            relay.deliverDue();   // not due yet: back-off
            assertThat(data.queryLong("SELECT attempts FROM outbox_events WHERE id = ?", poison)).isOne();

            clock.advance(Duration.ofSeconds(2));
            relay.deliverDue();
            assertThat(data.queryLong("SELECT attempts FROM outbox_events WHERE id = ?", poison)).isEqualTo(2);
        } finally {
            jdbc.update("DELETE FROM outbox_events WHERE id = ?", poison);
        }
    }

    @Test
    void a_failed_registration_leaves_neither_an_account_nor_an_event() {
        String email = TestData.uniqueEmail("ikinci");
        register(email, "Zeynep", "Ak");
        long eventsBefore = data.queryLong("SELECT count(*) FROM outbox_events");

        ApiClient browser = client();
        browser.get("/api/me");
        Response duplicate = browser.post("/api/auth/register",
            Map.of("email", email, "password", PASSWORD, "firstName", "Z", "lastName", "A"));

        assertThat(duplicate.code()).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(data.queryLong("SELECT count(*) FROM outbox_events")).isEqualTo(eventsBefore);
    }

    private long register(String email, String firstName, String lastName) {
        ApiClient browser = client();
        browser.get("/api/me");   // hands out the XSRF-TOKEN cookie
        Response created = browser.post("/api/auth/register",
            Map.of("email", email, "password", PASSWORD, "firstName", firstName, "lastName", lastName));
        assertThat(created.status()).isEqualTo(201);
        return created.body().get("id").asLong();
    }

    private long outboxRowsFor(long accountId) {
        return data.queryLong("""
            SELECT count(*) FROM outbox_events
            WHERE event_type = 'tr.gov.kurum.contracts.identity.CustomerRegistered' AND payload LIKE ?
              AND published_at IS NULL
            """, "%\"value\":" + accountId + "}%");
    }
}
