package tr.gov.kurum.identity.infrastructure.adapter.in.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.identity.application.port.in.PermissionCatalogEntry;
import tr.gov.kurum.identity.application.port.in.PermissionCatalogQueryUseCase;

/**
 * Lists the permission catalog for admin screens. URL rule requires {@code TYPE_ADMIN}; no extra
 * permission — any admin (including read-only destek) may inspect the catalog.
 */
@RestController
@RequestMapping("/api/admin")
class PermissionCatalogController {

    private final PermissionCatalogQueryUseCase catalog;

    PermissionCatalogController(PermissionCatalogQueryUseCase catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/permissions")
    List<PermissionCatalogEntry> permissions() {
        return catalog.listCatalog();
    }
}
