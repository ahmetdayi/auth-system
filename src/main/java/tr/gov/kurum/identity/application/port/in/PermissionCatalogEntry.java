package tr.gov.kurum.identity.application.port.in;

/** One row of the {@code permissions} catalog (name / description / group). */
public record PermissionCatalogEntry(String name, String description, String group) {
}
