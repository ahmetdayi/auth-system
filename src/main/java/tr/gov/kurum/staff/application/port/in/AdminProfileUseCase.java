package tr.gov.kurum.staff.application.port.in;

import tr.gov.kurum.common.domain.AccountId;

public interface AdminProfileUseCase {

    /** @throws tr.gov.kurum.common.domain.EntityNotFoundException when the admin has no profile */
    AdminProfileView getProfile(AccountId adminId);
}
