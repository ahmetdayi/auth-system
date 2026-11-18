package tr.gov.kurum.common.web;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;

/**
 * The caller of a request, as the identity context's token filter established it. Lives in the web part of
 * the shared kernel so that every context's controllers can use it without depending on the identity context
 * (rule 8: universal identity types). It deliberately carries no profile data.
 *
 * <p>When {@link #actorId()} is present the caller is in a support (act-as) session: {@link #accountId()} is
 * the customer being helped and {@code actorId} is the SUPPORT agent.</p>
 *
 * <p>Never serialized (the application is stateless), so it does not implement {@code Serializable}.</p>
 */
public record AuthenticatedAccount(
        AccountId accountId,
        AccountType type,
        long sessionId,
        String email,
        String tokenId,
        Instant tokenExpiresAt,
        AccountId actorId,
        Long grantId) {

    public AuthenticatedAccount {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(tokenExpiresAt, "tokenExpiresAt");
        if ((actorId == null) != (grantId == null)) {
            throw new IllegalArgumentException("actorId and grantId must both be set or both be null");
        }
    }

    /** Normal (non-support) session. */
    public static AuthenticatedAccount of(AccountId accountId, AccountType type, long sessionId, String email,
                                          String tokenId, Instant tokenExpiresAt) {
        return new AuthenticatedAccount(accountId, type, sessionId, email, tokenId, tokenExpiresAt, null, null);
    }

    public static AuthenticatedAccount supportSession(AccountId customerId, long sessionId, String email,
                                                      String tokenId, Instant tokenExpiresAt,
                                                      AccountId actorId, long grantId) {
        return new AuthenticatedAccount(customerId, AccountType.CUSTOMER, sessionId, email, tokenId,
            tokenExpiresAt, Objects.requireNonNull(actorId, "actorId"), grantId);
    }

    public boolean is(AccountType expected) {
        return type == expected;
    }

    public boolean isSupportSession() {
        return actorId != null;
    }

    public Optional<AccountId> actorIdOptional() {
        return Optional.ofNullable(actorId);
    }

    public Optional<Long> grantIdOptional() {
        return Optional.ofNullable(grantId);
    }

    /** @throws AccessDeniedException (403) when the caller is not a customer */
    public AccountId requireCustomer() {
        return require(AccountType.CUSTOMER);
    }

    /** @throws AccessDeniedException (403) when the caller is not an admin */
    public AccountId requireAdmin() {
        return require(AccountType.ADMIN);
    }

    /** @throws AccessDeniedException (403) when the caller is not a support agent (own token, not act-as) */
    public AccountId requireSupport() {
        if (isSupportSession()) {
            throw new AccessDeniedException("SUPPORT endpoint");
        }
        return require(AccountType.SUPPORT);
    }

    /** Refuses ops that must use the customer's own token (e.g. grant approve/deny). */
    public void refuseIfSupportSession() {
        if (isSupportSession()) {
            throw new AccessDeniedException("not allowed in support session");
        }
    }

    private AccountId require(AccountType expected) {
        if (type != expected) {
            throw new AccessDeniedException(expected + " endpoint");
        }
        return accountId;
    }

    /** Spring Security uses this as the authentication name (e.g. in logs): no e-mail address in logs. */
    @Override
    public String toString() {
        if (actorId != null) {
            return "SUPPORT#" + actorId.value() + "→CUSTOMER#" + accountId.value();
        }
        return type + "#" + accountId.value();
    }
}
