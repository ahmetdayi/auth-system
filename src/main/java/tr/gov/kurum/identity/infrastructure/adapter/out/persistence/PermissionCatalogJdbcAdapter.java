package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tr.gov.kurum.identity.application.port.in.PermissionCatalogEntry;
import tr.gov.kurum.identity.application.port.out.PermissionCatalog;

@Component
class PermissionCatalogJdbcAdapter implements PermissionCatalog {

    private final JdbcTemplate jdbc;

    PermissionCatalogJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PermissionCatalogEntry> findAllOrdered() {
        return jdbc.query("""
                SELECT name, description, group_name
                FROM permissions
                ORDER BY group_name, name
                """, (rs, rowNum) -> new PermissionCatalogEntry(
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("group_name")));
    }
}
