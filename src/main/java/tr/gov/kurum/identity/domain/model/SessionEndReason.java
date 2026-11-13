package tr.gov.kurum.identity.domain.model;

public enum SessionEndReason {
    LOGOUT,
    REVOKED_BY_OWNER,
    REVOKED_BY_ADMIN,
    TOKEN_REUSE_DETECTED,
    ACCOUNT_INVALIDATED
}
