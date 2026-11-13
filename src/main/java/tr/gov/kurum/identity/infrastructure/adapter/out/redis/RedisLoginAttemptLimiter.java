package tr.gov.kurum.identity.infrastructure.adapter.out.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.EmailAddress;
import tr.gov.kurum.identity.application.port.out.LoginAttemptLimiter;

/**
 * Fixed-window counters in Redis, shared by every instance. Keys contain a hash of the e-mail address rather
 * than the address itself, so Redis holds no personal data in plain text.
 */
@Component
@EnableConfigurationProperties(LoginThrottleProperties.class)
class RedisLoginAttemptLimiter implements LoginAttemptLimiter {

    private static final String ACCOUNT_KEY_PREFIX = "auth:login-fail:account:";
    private static final String IP_KEY_PREFIX = "auth:login-fail:ip:";

    /** INCR and PEXPIRE atomically: a crash between them cannot leave a counter that never expires. */
    private static final RedisScript<Long> COUNT_FAILURE = RedisScript.of("""
        for i, key in ipairs(KEYS) do
          local count = redis.call('INCR', key)
          if count == 1 then
            redis.call('PEXPIRE', key, ARGV[1])
          end
        end
        return 1
        """, Long.class);

    private final StringRedisTemplate redis;
    private final LoginThrottleProperties properties;

    RedisLoginAttemptLimiter(StringRedisTemplate redis, LoginThrottleProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public boolean isBlocked(EmailAddress email, String clientIp) {
        List<String> counts = redis.opsForValue().multiGet(List.of(accountKey(email, clientIp), ipKey(clientIp)));
        if (counts == null) {
            throw new IllegalStateException("Redis returned no result for the login throttle lookup");
        }
        return count(counts.get(0)) >= properties.maxFailuresPerAccount()
            || count(counts.get(1)) >= properties.maxFailuresPerIp();
    }

    @Override
    public void recordFailure(EmailAddress email, String clientIp) {
        redis.execute(COUNT_FAILURE, List.of(accountKey(email, clientIp), ipKey(clientIp)),
            Long.toString(properties.window().toMillis()));
    }

    @Override
    public void recordSuccess(EmailAddress email, String clientIp) {
        // Only the pair counter is cleared: one successful login must not reset the IP-wide counter.
        redis.delete(accountKey(email, clientIp));
    }

    private static long count(String value) {
        return value == null ? 0 : Long.parseLong(value);
    }

    private static String accountKey(EmailAddress email, String clientIp) {
        return ACCOUNT_KEY_PREFIX + sha256(email.value()) + ":" + clientIp;
    }

    private static String ipKey(String clientIp) {
        return IP_KEY_PREFIX + clientIp;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
