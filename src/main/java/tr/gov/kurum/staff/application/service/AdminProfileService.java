package tr.gov.kurum.staff.application.service;

import org.springframework.stereotype.Service;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.staff.application.port.in.AdminProfileUseCase;
import tr.gov.kurum.staff.application.port.in.AdminProfileView;
import tr.gov.kurum.staff.application.port.out.AdminProfileRepository;
import tr.gov.kurum.staff.domain.model.StaffErrorKey;

@Service
public class AdminProfileService implements AdminProfileUseCase {

    private final AdminProfileRepository repository;

    AdminProfileService(AdminProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public AdminProfileView getProfile(AccountId adminId) {
        return repository.findById(adminId)
            .map(AdminProfileView::of)
            .orElseThrow(() -> new EntityNotFoundException(StaffErrorKey.ADMIN_PROFILE_NOT_FOUND, adminId.value()));
    }
}
