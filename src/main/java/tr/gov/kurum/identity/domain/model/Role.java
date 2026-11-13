package tr.gov.kurum.identity.domain.model;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import tr.gov.kurum.common.domain.AggregateRoot;

/** Role aggregate root (reference data). Customers reference roles by {@link RoleId} only. */
public final class Role extends AggregateRoot {

    private final RoleId id;
    private final RoleName name;
    private final Set<PermissionName> permissions;

    private Role(RoleId id, long version, RoleName name, Set<PermissionName> permissions) {
        super(version);
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.permissions = new LinkedHashSet<>(permissions);
    }

    public static Role restore(RoleId id, long version, RoleName name, Set<PermissionName> permissions) {
        return new Role(id, version, name, permissions);
    }

    public RoleId id() {
        return id;
    }

    public RoleName name() {
        return name;
    }

    public Set<PermissionName> permissions() {
        return Set.copyOf(permissions);
    }
}
