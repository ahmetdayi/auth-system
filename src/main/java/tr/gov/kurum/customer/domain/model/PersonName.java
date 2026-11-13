package tr.gov.kurum.customer.domain.model;

import tr.gov.kurum.common.domain.BusinessRuleException;

public record PersonName(String firstName, String lastName) {

    private static final int MAX_LENGTH = 100;

    public PersonName {
        firstName = normalize(firstName);
        lastName = normalize(lastName);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    private static String normalize(String part) {
        if (part == null || part.isBlank()) {
            throw new BusinessRuleException(CustomerErrorKey.INVALID_PERSON_NAME);
        }
        String trimmed = part.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new BusinessRuleException(CustomerErrorKey.INVALID_PERSON_NAME);
        }
        return trimmed;
    }
}
