package tr.gov.kurum.identity.application.service;

import java.util.List;
import org.springframework.stereotype.Service;
import tr.gov.kurum.identity.application.port.in.PermissionCatalogEntry;
import tr.gov.kurum.identity.application.port.in.PermissionCatalogQueryUseCase;
import tr.gov.kurum.identity.application.port.out.PermissionCatalog;

@Service
public class PermissionCatalogQueryService implements PermissionCatalogQueryUseCase {

    private final PermissionCatalog catalog;

    PermissionCatalogQueryService(PermissionCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<PermissionCatalogEntry> listCatalog() {
        return catalog.findAllOrdered();
    }
}
