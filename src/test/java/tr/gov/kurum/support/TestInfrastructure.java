package tr.gov.kurum.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL and Redis for the integration tests; H2 or an embedded Redis would not show PostgreSQL's
 * constraint, locking and SKIP LOCKED behaviour (coding rule 1.26).
 *
 * <p>Default: Testcontainers (needs Docker), started once per JVM and shared by all test classes. Where
 * Docker is not available (some CI runners), already running servers can be used instead by setting
 * {@code IT_DB_URL}, {@code IT_DB_USERNAME}, {@code IT_DB_PASSWORD}, {@code IT_REDIS_HOST},
 * {@code IT_REDIS_PORT} and optionally {@code IT_REDIS_DATABASE}. Use a dedicated, empty database: the tests
 * write to it.</p>
 */
public final class TestInfrastructure {

    private static PostgreSQLContainer postgres;
    private static GenericContainer<?> redis;

    private TestInfrastructure() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        String externalDatabase = System.getenv("IT_DB_URL");
        if (externalDatabase != null && !externalDatabase.isBlank()) {
            registry.add("spring.datasource.url", () -> externalDatabase);
            registry.add("spring.datasource.username", () -> env("IT_DB_USERNAME", "postgres"));
            registry.add("spring.datasource.password", () -> env("IT_DB_PASSWORD", ""));
            registry.add("spring.data.redis.host", () -> env("IT_REDIS_HOST", "localhost"));
            registry.add("spring.data.redis.port", () -> env("IT_REDIS_PORT", "6379"));
            registry.add("spring.data.redis.database", () -> env("IT_REDIS_DATABASE", "0"));
            return;
        }
        startContainers();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    private static synchronized void startContainers() {
        if (postgres != null) {
            return;
        }
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        // Stopped by Testcontainers' cleanup container when the JVM exits.
        Startables.deepStart(postgres, redis).join();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
