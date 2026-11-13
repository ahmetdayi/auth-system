package tr.gov.kurum.common.domain;

/**
 * Identity of an account, shared by every bounded context (rule 8: universal identity types belong to the
 * shared kernel). The identity context owns the account; customer, membership and staff key their own
 * aggregates by the same number instead of holding a reference to the account object (rule 5.2).
 */
public record AccountId(long value) {

    public AccountId {
        if (value <= 0) {
            throw new BusinessRuleException(CommonErrorKey.INVALID_IDENTIFIER, value);
        }
    }
}
