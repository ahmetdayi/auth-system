package tr.gov.kurum.common.domain;

/**
 * Kinds of account. Every context may branch on it; only the identity context assigns it.
 * {@code SUPPORT} is not an admin: no {@code TYPE_ADMIN}, no {@code /api/admin/**}.
 */
public enum AccountType {
    CUSTOMER,
    ADMIN,
    SUPPORT
}
