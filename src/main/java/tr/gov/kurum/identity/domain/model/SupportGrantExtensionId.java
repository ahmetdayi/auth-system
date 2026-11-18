package tr.gov.kurum.identity.domain.model;

public record SupportGrantExtensionId(long value) {

    public SupportGrantExtensionId {
        Identifiers.requirePositive(value);
    }
}
