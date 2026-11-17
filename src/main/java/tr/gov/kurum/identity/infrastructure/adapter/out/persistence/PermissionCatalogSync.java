package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tr.gov.kurum.identity.domain.model.KnownPermission;

/**
 * Upserts {@link KnownPermission} into {@code permissions} so the DB catalog matches the code. Assignments
 * are untouched; only name/description/group of the catalog rows are refreshed.
 */
@Component
class PermissionCatalogSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalogSync.class);

    private static final String UPSERT = """
            INSERT INTO permissions (name, description, group_name) VALUES (?, ?, ?)
            ON CONFLICT (name) DO UPDATE
            SET description = EXCLUDED.description,
                group_name = EXCLUDED.group_name
            """;

    private final JdbcTemplate jdbc;

    PermissionCatalogSync(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (KnownPermission permission : KnownPermission.values()) {
            jdbc.update(UPSERT, permission.code(), permission.description(), permission.group());
        }
        log.info("Permission catalog synced: {} entries", KnownPermission.values().length);
    }
}
