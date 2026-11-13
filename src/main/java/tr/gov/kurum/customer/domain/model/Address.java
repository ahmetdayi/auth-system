package tr.gov.kurum.customer.domain.model;

import java.util.Objects;
import tr.gov.kurum.common.domain.BusinessRuleException;

/** Child entity of {@link CustomerProfile}. Created and removed only through the aggregate root. */
public final class Address {

    private static final int MAX_LABEL = 40;
    private static final int MAX_CITY = 80;
    private static final int MAX_LINE = 200;

    private final AddressId id;
    private final String label;
    private final String city;
    private final String line;

    private Address(AddressId id, String label, String city, String line) {
        this.id = Objects.requireNonNull(id, "id");
        this.label = requireText(label, MAX_LABEL);
        this.city = requireText(city, MAX_CITY);
        this.line = requireText(line, MAX_LINE);
    }

    static Address create(AddressId id, String label, String city, String line) {
        return new Address(id, label, city, line);
    }

    /** Rebuilds an address from storage. */
    public static Address restore(AddressId id, String label, String city, String line) {
        return new Address(id, label, city, line);
    }

    public AddressId id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String city() {
        return city;
    }

    public String line() {
        return line;
    }

    private static String requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new BusinessRuleException(CustomerErrorKey.INVALID_ADDRESS);
        }
        return value.trim();
    }
}
