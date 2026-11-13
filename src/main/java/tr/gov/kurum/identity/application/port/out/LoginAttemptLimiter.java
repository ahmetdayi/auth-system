package tr.gov.kurum.identity.application.port.out;

import tr.gov.kurum.common.domain.EmailAddress;

/**
 * Brute-force protection. Counted per (e-mail, IP) — so an attacker cannot lock a victim out from another IP —
 * and per IP, to slow down credential stuffing across many accounts.
 */
public interface LoginAttemptLimiter {

    boolean isBlocked(EmailAddress email, String clientIp);

    void recordFailure(EmailAddress email, String clientIp);

    void recordSuccess(EmailAddress email, String clientIp);
}
