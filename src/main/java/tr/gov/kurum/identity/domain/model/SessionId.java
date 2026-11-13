package tr.gov.kurum.identity.domain.model;

public record SessionId(long value) {

    public SessionId {
        Identifiers.requirePositive(value);
    }
}
