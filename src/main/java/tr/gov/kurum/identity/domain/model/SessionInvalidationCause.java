package tr.gov.kurum.identity.domain.model;

/** Why every session of an account was invalidated at once. */
public enum SessionInvalidationCause {
    PASSWORD_CHANGED,
    ACCOUNT_DISABLED,
    LOGOUT_EVERYWHERE,
    TERMINATED_BY_ADMIN
}
