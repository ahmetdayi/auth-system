package tr.gov.kurum.identity.infrastructure.adapter.out.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.identity.application.port.out.AccessTokenRevocationStore;
import tr.gov.kurum.identity.infrastructure.adapter.out.token.TokenProperties;

/**
 * Two kinds of entries, both expiring by themselves so the store never grows without bound:
 * <ul>
 *   <li>{@code auth:revoked:jti:<id>} — one logged-out token, until that token would have expired;</li>
 *   <li>{@code auth:cutoff:<accountId>} — epoch millis; every token of the account issued up to it is
 *       revoked. Kept for one access-token lifetime plus a margin for clock skew: after that, no token from
 *       before the cutoff is still accepted anyway. Derived from the token settings, so it cannot be
 *       configured too short.</li>
 * </ul>
 * Redis errors are not swallowed: callers fail closed (the filter answers 503, writes roll back).
 */
@Component
class RedisAccessTokenRevocationStore implements AccessTokenRevocationStore {

    private static final String TOKEN_KEY_PREFIX = "auth:revoked:jti:";
    private static final String CUTOFF_KEY_PREFIX = "auth:cutoff:";
    private static final Duration CLOCK_SKEW_MARGIN = Duration.ofMinutes(1);

    /** Keeps the later cutoff when two invalidations race; never moves a cutoff backwards. */
    private static final RedisScript<Long> RAISE_CUTOFF = RedisScript.of("""
        local current = redis.call('GET', KEYS[1])
        if (not current) or (tonumber(current) < tonumber(ARGV[1])) then
          redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
          return 1
        end
        return 0
        """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Duration cutoffRetention;

    RedisAccessTokenRevocationStore(StringRedisTemplate redis, Clock clock, TokenProperties tokenProperties) {
        this.redis = redis;
        this.clock = clock;
        this.cutoffRetention = tokenProperties.accessTokenTtl().plus(CLOCK_SKEW_MARGIN);
    }

    @Override
    public void revokeToken(String tokenId, Instant expiresAt) {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return;   // already expired: nothing to revoke
        }
        redis.opsForValue().set(TOKEN_KEY_PREFIX + tokenId, "1", remaining);
    }

    @Override
    public void revokeTokensIssuedUntil(AccountId account, Instant cutoff) {
        redis.execute(RAISE_CUTOFF, List.of(cutoffKey(account)),
            Long.toString(cutoff.toEpochMilli()), Long.toString(cutoffRetention.toMillis()));
    }

    @Override
    public boolean isRevoked(String tokenId, AccountId account, Instant issuedAt) {
        List<String> values = redis.opsForValue().multiGet(List.of(TOKEN_KEY_PREFIX + tokenId, cutoffKey(account)));
        if (values == null) {
            // Only happens inside a pipeline/transaction; treat as a failure rather than "not revoked".
            throw new IllegalStateException("Redis returned no result for the revocation lookup");
        }
        if (values.get(0) != null) {
            return true;
        }
        String cutoff = values.get(1);
        // "<=": a token issued in the very millisecond of the cutoff is revoked too (fail-closed).
        return cutoff != null && issuedAt.toEpochMilli() <= Long.parseLong(cutoff);
    }

    private static String cutoffKey(AccountId account) {
        return CUTOFF_KEY_PREFIX + account.value();
    }
}
