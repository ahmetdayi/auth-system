package tr.gov.kurum.identity.infrastructure.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.domain.EntityNotFoundException;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.OptimisticVersions;
import tr.gov.kurum.identity.application.port.out.LocatedSession;
import tr.gov.kurum.identity.application.port.out.UserSessionRepository;
import tr.gov.kurum.identity.domain.model.ClientDetails;
import tr.gov.kurum.identity.domain.model.IdentityErrorKey;
import tr.gov.kurum.identity.domain.model.PresentedRefreshToken;
import tr.gov.kurum.identity.domain.model.RefreshTokenHash;
import tr.gov.kurum.identity.domain.model.RetiredRefreshToken;
import tr.gov.kurum.identity.domain.model.SessionId;
import tr.gov.kurum.identity.domain.model.UserSession;
import tr.gov.kurum.identity.domain.model.UserSessionSnapshot;

@Component
@RequiredArgsConstructor
class UserSessionPersistenceAdapter implements UserSessionRepository {

    private final UserSessionJpaRepository repository;
    private final EntityManager entityManager;

    @Override
    public SessionId nextId() {
        return new SessionId(repository.nextId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocatedSession> findByPresentedToken(RefreshTokenHash hash) {
        Optional<LocatedSession> current = repository.findByCurrentTokenHash(hash.value())
            .map(entity -> new LocatedSession(toDomain(entity), PresentedRefreshToken.current(hash)));
        if (current.isPresent()) {
            return current;
        }
        RetiredRefreshTokenJpaEntity retired = entityManager.find(RetiredRefreshTokenJpaEntity.class, hash.value());
        if (retired == null) {
            return Optional.empty();
        }
        return repository.findById(retired.getSessionId()).map(entity ->
            new LocatedSession(toDomain(entity), PresentedRefreshToken.retired(hash, retired.getRetiredAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSession> findById(SessionId id) {
        return repository.findById(id.value()).map(UserSessionPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public void save(UserSession session) {
        if (session.isNew()) {
            UserSessionJpaEntity entity = new UserSessionJpaEntity(session.id().value());
            entity.setAccountId(session.owner().value());
            entity.setStartedAt(session.startedAt());
            apply(session, entity);
            repository.save(entity);
        } else {
            UserSessionJpaEntity entity = repository.findById(session.id().value())
                .orElseThrow(() -> new EntityNotFoundException(IdentityErrorKey.SESSION_NOT_FOUND, session.id().value()));
            OptimisticVersions.requireSameVersion(entity, session);
            apply(session, entity);
            // Flush the versioned UPDATE first: a concurrent rotation of the same session fails here with an
            // optimistic-locking error, before we try to insert the same retired token twice.
            repository.flush();
        }
        for (RetiredRefreshToken retired : session.retiredTokensSinceLoad()) {
            entityManager.persist(new RetiredRefreshTokenJpaEntity(
                retired.hash().value(), session.id().value(), retired.retiredAt()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSession> findUnendedByOwner(AccountId owner, Instant now, int limit) {
        return repository
            .findByAccountIdAndEndedAtIsNullAndExpiresAtAfterOrderByLastRefreshedAtDesc(
                owner.value(), now, Limit.of(limit))
            .stream()
            .map(UserSessionPersistenceAdapter::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now, int batchSize) {
        return repository.deleteExpiredBatch(now, batchSize);
    }

    private static void apply(UserSession session, UserSessionJpaEntity entity) {
        entity.setUserAgent(session.client().userAgent());
        entity.setIpAddress(session.client().ipAddress());
        entity.setLastRefreshedAt(session.lastRefreshedAt());
        entity.setExpiresAt(session.expiresAt());
        entity.setCurrentTokenHash(session.currentTokenHash().value());
        entity.setEndedAt(session.endedAt().orElse(null));
        entity.setEndReason(session.endReason().orElse(null));
    }

    private static UserSession toDomain(UserSessionJpaEntity entity) {
        return UserSession.restore(new UserSessionSnapshot(
            new SessionId(entity.getId()),
            entity.getVersion(),
            new AccountId(entity.getAccountId()),
            new ClientDetails(entity.getUserAgent(), entity.getIpAddress()),
            entity.getStartedAt(),
            entity.getLastRefreshedAt(),
            entity.getExpiresAt(),
            new RefreshTokenHash(entity.getCurrentTokenHash()),
            entity.getEndedAt(),
            entity.getEndReason()));
    }
}
