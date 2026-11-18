package tr.gov.kurum.identity.infrastructure.adapter.out.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.AccountType;
import tr.gov.kurum.identity.application.port.out.AccessTokenIssuer;
import tr.gov.kurum.identity.application.port.out.IssuedAccessToken;
import tr.gov.kurum.identity.domain.model.AccessProfile;
import tr.gov.kurum.identity.domain.model.SessionId;
import tr.gov.kurum.identity.domain.model.SupportGrantId;

/**
 * Issues and verifies access tokens (HS256 JWT). Refresh tokens are not JWTs, so a refresh token can never
 * pass {@link #verify(String)}, and the {@code token_type} claim keeps other JWTs signed with the same key
 * (e.g. a future password-reset token) from being accepted as access tokens.
 *
 * <p>jjwt's API works with {@link Date}; it is converted to {@link Instant} right at this boundary.</p>
 */
@Component
public class JwtAccessTokenService implements AccessTokenIssuer {

    static final String CLAIM_TOKEN_TYPE = "token_type";
    static final String CLAIM_ACCOUNT_TYPE = "account_type";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_AUTHORITIES = "authorities";
    static final String CLAIM_SESSION_ID = "sid";
    static final String CLAIM_ACTOR = "actor";
    static final String CLAIM_GRANT_ID = "grant_id";
    /**
     * The standard {@code iat} has whole-second precision. Revocation compares the issue time with a cutoff in
     * milliseconds; with seconds only, a token issued 300 ms after a password change could fall "before" it
     * (and be rejected) or one issued 300 ms before a ban could fall "after" it (and stay valid).
     */
    static final String CLAIM_ISSUED_AT_MILLIS = "iat_ms";
    static final String ACCESS_TOKEN_TYPE = "access";
    static final String SUPPORT_ACCESS_TOKEN_TYPE = "support_access";

    private static final Logger log = LoggerFactory.getLogger(JwtAccessTokenService.class);
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final TokenProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtAccessTokenService(TokenProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = resolveSigningKey(properties);
        this.parser = Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(properties.issuer())
            .clockSkewSeconds(CLOCK_SKEW_SECONDS)
            .clock(() -> Date.from(clock.instant()))
            .build();
    }

    @Override
    public IssuedAccessToken issue(AccessProfile profile, SessionId sessionId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        String token = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .issuer(properties.issuer())
            .subject(Long.toString(profile.accountId().value()))
            .claim(CLAIM_TOKEN_TYPE, ACCESS_TOKEN_TYPE)
            .claim(CLAIM_ACCOUNT_TYPE, profile.type().name())
            .claim(CLAIM_EMAIL, profile.email().value())
            .claim(CLAIM_AUTHORITIES, AuthorityNames.of(profile))
            .claim(CLAIM_ISSUED_AT_MILLIS, now.toEpochMilli())
            .claim(CLAIM_SESSION_ID, sessionId.value())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
        return new IssuedAccessToken(token, expiresAt);
    }

    @Override
    public IssuedAccessToken issueSupport(AccountId customerId, String customerEmail, AccountId actorId,
                                          SupportGrantId grantId, Collection<String> authorities,
                                          SessionId agentSessionId, Instant grantExpiresAt) {
        Instant now = clock.instant();
        Instant accessExpiry = now.plus(properties.accessTokenTtl());
        Instant expiresAt = grantExpiresAt.isBefore(accessExpiry) ? grantExpiresAt : accessExpiry;
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("grant already expired");
        }
        String token = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .issuer(properties.issuer())
            .subject(Long.toString(customerId.value()))
            .claim(CLAIM_TOKEN_TYPE, SUPPORT_ACCESS_TOKEN_TYPE)
            .claim(CLAIM_ACCOUNT_TYPE, AccountType.CUSTOMER.name())
            .claim(CLAIM_EMAIL, customerEmail)
            .claim(CLAIM_AUTHORITIES, List.copyOf(authorities))
            .claim(CLAIM_ISSUED_AT_MILLIS, now.toEpochMilli())
            .claim(CLAIM_SESSION_ID, agentSessionId.value())
            .claim(CLAIM_ACTOR, actorId.value())
            .claim(CLAIM_GRANT_ID, grantId.value())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
        return new IssuedAccessToken(token, expiresAt);
    }

    /**
     * Verifies signature, expiry, issuer, token type and the required claims.
     *
     * @throws JwtException (or a subclass such as {@code ExpiredJwtException}) for every invalid token
     */
    public VerifiedAccessToken verify(String token) {
        if (token == null || token.isBlank()) {
            throw new MalformedJwtException("empty token");
        }
        Claims claims = parser.parseSignedClaims(token).getPayload();
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        boolean support = SUPPORT_ACCESS_TOKEN_TYPE.equals(tokenType);
        if (!ACCESS_TOKEN_TYPE.equals(tokenType) && !support) {
            throw new MalformedJwtException("not an access token");
        }
        String accountType = claims.get(CLAIM_ACCOUNT_TYPE, String.class);
        String email = claims.get(CLAIM_EMAIL, String.class);
        List<?> rawAuthorities = claims.get(CLAIM_AUTHORITIES, List.class);
        Object issuedAtMillis = claims.get(CLAIM_ISSUED_AT_MILLIS);
        Object sessionIdClaim = claims.get(CLAIM_SESSION_ID);
        if (claims.getId() == null || claims.getSubject() == null || accountType == null || email == null
                || rawAuthorities == null || !(issuedAtMillis instanceof Number issuedAt)
                || !(sessionIdClaim instanceof Number sessionNumber) || claims.getExpiration() == null) {
            throw new MalformedJwtException("missing claim");
        }
        AccountId account;
        AccountType type;
        SessionId sessionId;
        AccountId actorId = null;
        Long grantId = null;
        try {
            account = new AccountId(Long.parseLong(claims.getSubject()));
            type = AccountType.valueOf(accountType);
            sessionId = new SessionId(sessionNumber.longValue());
            if (support) {
                Object actorClaim = claims.get(CLAIM_ACTOR);
                Object grantClaim = claims.get(CLAIM_GRANT_ID);
                if (!(actorClaim instanceof Number actorNumber) || !(grantClaim instanceof Number grantNumber)) {
                    throw new MalformedJwtException("missing support claim");
                }
                actorId = new AccountId(actorNumber.longValue());
                grantId = grantNumber.longValue();
            }
        } catch (IllegalArgumentException e) {   // NumberFormatException is an IllegalArgumentException
            throw new MalformedJwtException("invalid subject or user type", e);
        } catch (RuntimeException e) {          // e.g. a non-positive id rejected by AccountId
            throw new MalformedJwtException("invalid subject", e);
        }
        List<String> authorities = rawAuthorities.stream().map(String::valueOf).toList();
        return new VerifiedAccessToken(claims.getId(), account, type, sessionId, email, authorities,
            Instant.ofEpochMilli(issuedAt.longValue()), claims.getExpiration().toInstant(), actorId, grantId);
    }

    /** For logout: an expired or malformed token simply needs no revocation. */
    public Optional<VerifiedAccessToken> verifyLeniently(String token) {
        try {
            return Optional.of(verify(token));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static SecretKey resolveSigningKey(TokenProperties properties) {
        if (properties.jwtSecret() != null && !properties.jwtSecret().isBlank()) {
            // Throws WeakKeyException below 256 bits: the application refuses to start with a weak key.
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.jwtSecret()));
        }
        if (properties.generateEphemeralSecretIfMissing()) {
            log.warn("No JWT secret configured; using an ephemeral key. Tokens become invalid on restart. "
                + "Local development only.");
            return Jwts.SIG.HS256.key().build();
        }
        throw new IllegalStateException("app.token.jwt-secret (env JWT_SECRET) must be set");
    }
}
