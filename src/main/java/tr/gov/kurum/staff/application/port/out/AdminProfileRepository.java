package tr.gov.kurum.staff.application.port.out;

import java.util.Optional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.staff.domain.model.AdminProfile;

public interface AdminProfileRepository {

    Optional<AdminProfile> findById(AccountId id);
}
