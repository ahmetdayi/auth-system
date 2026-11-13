package tr.gov.kurum.identity.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import tr.gov.kurum.identity.domain.model.Role;
import tr.gov.kurum.identity.domain.model.RoleId;
import tr.gov.kurum.identity.domain.model.RoleName;

public interface RoleRepository {

    Optional<Role> findByName(RoleName name);

    List<Role> findAllById(Set<RoleId> ids);
}
