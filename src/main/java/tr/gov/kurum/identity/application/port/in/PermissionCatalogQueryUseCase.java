package tr.gov.kurum.identity.application.port.in;

import java.util.List;

/** Read model over the permission catalog table (synced from {@code KnownPermission} at startup). */
public interface PermissionCatalogQueryUseCase {

    List<PermissionCatalogEntry> listCatalog();
}
