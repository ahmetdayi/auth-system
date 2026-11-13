package tr.gov.kurum.common.domain;

/** The two kinds of account. Every context may branch on it; only the identity context assigns it. */
public enum AccountType {
    CUSTOMER,
    ADMIN
}
