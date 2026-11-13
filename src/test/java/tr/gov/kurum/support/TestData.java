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
        long id = nextAccountId();
        String email = uniqueEmail("customer");
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
            jdbc.update("""
                    INSERT INTO role_permissions (role_id, permission_name)
                    SELECT id, ? FROM roles WHERE name = ? ON CONFLICT DO NOTHING
                    """, permission, roleName);
        }
        jdbc.update("INSERT INTO account_roles (account_id, role_id) SELECT ?, id FROM roles WHERE name = ?",
            account.id(), roleName);
    }

    /** An admin account (identity) with direct permissions, plus its staff profile. */
    public Account admin(String level, String... permissions) {
        long id = nextAccountId();
        String email = uniqueEmail("admin");
        OffsetDateTime now = now();
        insertAccount(id, email, "ADMIN", level, now);
        for (String permission : permissions) {
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

    public long queryLong(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    private long nextAccountId() {
        Long id = jdbc.queryForObject("SELECT nextval('account_id_seq')", Long.class);
        if (id == null) {
            throw new IllegalStateException("account_id_seq returned null");
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
