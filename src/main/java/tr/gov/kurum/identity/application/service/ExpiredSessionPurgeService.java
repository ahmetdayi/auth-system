package tr.gov.kurum.identity.application.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tr.gov.kurum.identity.application.port.in.PurgeExpiredSessionsUseCase;
import tr.gov.kurum.identity.application.port.out.UserSessionRepository;

/**
 * Removes expired sessions. Ended-but-unexpired sessions are kept on purpose: their retired tokens are the
 * evidence that reuse detection needs.
 */
@Service
@RequiredArgsConstructor
public class ExpiredSessionPurgeService implements PurgeExpiredSessionsUseCase {

    private final UserSessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public int purgeExpiredSessions(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        Integer deleted = transactionTemplate.execute(status ->
            sessionRepository.deleteExpired(clock.instant(), batchSize));
        return deleted == null ? 0 : deleted;
    }
}
