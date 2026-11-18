package tr.gov.kurum.identity.domain.model;

public record SupportGrantId(long value) {

    public SupportGrantId {
        Identifiers.requirePositive(value);
    }
}
