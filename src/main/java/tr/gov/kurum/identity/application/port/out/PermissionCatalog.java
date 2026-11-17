package tr.gov.kurum.identity.application.port.out;

import java.util.List;
import tr.gov.kurum.identity.application.port.in.PermissionCatalogEntry;

/** Persistence port for the {@code permissions} catalog table. */
public interface PermissionCatalog {

    List<PermissionCatalogEntry> findAllOrdered();
}
