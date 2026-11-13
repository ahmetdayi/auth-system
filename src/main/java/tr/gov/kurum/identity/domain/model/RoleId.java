package tr.gov.kurum.identity.domain.model;

public record RoleId(long value) {

    public RoleId {
        Identifiers.requirePositive(value);
    }
}
