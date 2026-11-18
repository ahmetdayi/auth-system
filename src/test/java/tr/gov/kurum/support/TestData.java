package tr.gov.kurum.support;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Creates accounts straight in the database, the way an operator would (there is no admin sign-up endpoint).
 * Writes into the tables of several modules on purpose: test setup is not bound by the module boundaries.
 * Every account gets a unique e-mail, so tests never depend on each other's data.
 */
public final class TestData {

    public static final String PASSWORD = "TestSifresi-123";
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final String passwordHash = new BCryptPasswordEncoder(4).encode(PASSWORD);

    public TestData(JdbcTemplate jdbc, MutableClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Account(long id, String email, String type) {
    }

    /** A client IP of its own, so login-throttle counters of different tests never meet. */
    public static String uniqueIp() {
        int n = IP_SEQUENCE.incrementAndGet();
        return "10." + (n >> 16 & 255) + "." + (n >> 8 & 255) + "." + (n & 255);
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12) + "@test.example.com";
    }

    /**
     * A customer as registration leaves it once the outbox has delivered: account (identity) plus profile
     * (customer). Tests of the delivery itself register through the API instead.
     */
    public Account customer() {
        return customerWithEmailPrefix("customer");
    }

    /** Same as {@link #customer()} but e-mails share a searchable prefix (for directory filters). */
    public Account customerWithEmailPrefix(String emailPrefix) {
        long id = nextAccountId();
        String email = uniqueEmail(emailPrefix);
        OffsetDateTime now = now();
        insertAccount(id, email, "CUSTOMER", null, now);
        jdbc.update("INSERT INTO account_roles (account_id, role_id) SELECT ?, id FROM roles WHERE name = 'USER'",
            id);
        jdbc.update("""
                INSERT INTO customer_profiles (account_id, contact_email, first_name, last_name, loyalty_points,
                                               registered_at, created_at, updated_at, version)
                VALUES (?, ?, 'Test', 'Müşteri', 0, ?, ?, ?, 0)
                """, id, email, now, now, now);
        return new Account(id, email, "CUSTOMER");
    }

    public Account customerWithMembership(String tier, Instant endsAtOrNull) {
        Account account = customer();
        OffsetDateTime now = now();
        jdbc.update("""
                INSERT INTO memberships (account_id, tier, starts_at, ends_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """, account.id(), tier, now.minusDays(30), endsAtOrNull == null ? null : at(endsAtOrNull), now, now);
        return account;
    }

    public void assignRole(Account account, String roleName, String... permissions) {
        jdbc.update("""
                INSERT INTO roles (id, name, created_at, updated_at, version)
                VALUES (nextval('role_id_seq'), ?, now(), now(), 0) ON CONFLICT (name) DO NOTHING
                """, roleName);
        for (String permission : permissions) {
            ensurePermission(permission);
            jdbc.update("""
                    INSERT INTO role_permissions (role_id, permission_name)
                    SELECT id, ? FROM roles WHERE name = ? ON CONFLICT DO NOTHING
                    """, permission, roleName);
        }
        jdbc.update("INSERT INTO account_roles (account_id, role_id) SELECT ?, id FROM roles WHERE name = ?",
            account.id(), roleName);
    }

    /** A SUPPORT agent account (no admin profile, no admin_level). */
    public Account support() {
        long id = nextAccountId();
        String email = uniqueEmail("support");
        OffsetDateTime now = now();
        insertAccount(id, email, "SUPPORT", null, now);
        return new Account(id, email, "SUPPORT");
    }

    /** An admin account (identity) with direct permissions, plus its staff profile. */
    public Account admin(String level, String... permissions) {
        long id = nextAccountId();
        String email = uniqueEmail("admin");
        OffsetDateTime now = now();
        insertAccount(id, email, "ADMIN", level, now);
        for (String permission : permissions) {
            ensurePermission(permission);
            jdbc.update("INSERT INTO account_permissions (account_id, permission_name) VALUES (?, ?)", id,
                permission);
        }
        jdbc.update("""
                INSERT INTO admin_profiles (account_id, full_name, department, created_at, updated_at, version)
                VALUES (?, 'Test Admin', 'Test', ?, ?, 0)
                """, id, now, now);
        return new Account(id, email, "ADMIN");
    }

    private void insertAccount(long id, String email, String type, String adminLevel, OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO accounts (id, email, password_hash, account_type, admin_level, status,
                                      sessions_valid_after, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, 0)
                """, id, email, passwordHash, type, adminLevel, now.minusMinutes(1), now, now);
    }

    /** Satisfies the FK into {@code permissions} when tests assign codes ahead of catalog sync. */
    private void ensurePermission(String permission) {
        jdbc.update("""
                INSERT INTO permissions (name, description, group_name) VALUES (?, ?, 'test')
                ON CONFLICT (name) DO NOTHING
                """, permission, permission);
    }

    public long queryLong(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    /**
     * An already-ended grant with audit extensions. Multiple ended grants per customer are allowed
     * (the live unique index does not apply). Used to seed list N+1 scenarios.
     */
    public long endedGrantWithExtensions(Account agent, Account customer, int extensionCount) {
        if (extensionCount < 0) {
            throw new IllegalArgumentException("extensionCount");
        }
        long grantId = nextId("support_grant_id_seq");
        OffsetDateTime requested = now().minusHours(2);
        OffsetDateTime started = requested.plusMinutes(1);
        OffsetDateTime ended = started.plusMinutes(20);
        jdbc.update("""
                INSERT INTO support_grants (id, agent_id, customer_id, status, scopes, initial_minutes,
                                            extension_count, expires_at, requested_at, started_at, ended_at,
                                            end_reason, created_at, updated_at, version)
                VALUES (?, ?, ?, 'ENDED',
                        'support:profile:read,support:profile:write,support:content:read,support:full',
                        20, ?, ?, ?, ?, ?, 'AGENT_ENDED', ?, ?, 0)
                """, grantId, agent.id(), customer.id(), extensionCount, ended, requested, started, ended,
            now(), now());
        for (int i = 0; i < extensionCount; i++) {
            long extensionId = nextId("support_grant_extension_id_seq");
            OffsetDateTime extRequested = started.plusMinutes(5L * (i + 1));
            jdbc.update("""
                    INSERT INTO support_grant_extensions (id, grant_id, requested_minutes, requested_at,
                                                          decision, decided_at, created_at, updated_at, version)
                    VALUES (?, ?, 20, ?, 'APPROVED', ?, ?, ?, 0)
                    """, extensionId, grantId, extRequested, extRequested.plusSeconds(30), now(), now());
        }
        return grantId;
    }

    /** Extra addresses on a customer profile (for proving directory listing does not load them). */
    public void addAddresses(Account customer, int count) {
        if (count < 0 || count > 5) {
            throw new IllegalArgumentException("count must be 0..5");
        }
        OffsetDateTime now = now();
        for (int i = 0; i < count; i++) {
            long id = nextId("customer_address_id_seq");
            jdbc.update("""
                    INSERT INTO customer_addresses (id, customer_id, label, city, line, created_at, updated_at,
                                                    version)
                    VALUES (?, ?, ?, 'Ankara', 'Test Cad. No: ' || ?, ?, ?, 0)
                    """, id, customer.id(), "Adr-" + i, i, now, now);
        }
    }

    private long nextAccountId() {
        return nextId("account_id_seq");
    }

    private long nextId(String sequence) {
        Long id = jdbc.queryForObject("SELECT nextval('" + sequence + "')", Long.class);
        if (id == null) {
            throw new IllegalStateException(sequence + " returned null");
        }
        return id;
    }

    private OffsetDateTime now() {
        return at(clock.instant());
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
