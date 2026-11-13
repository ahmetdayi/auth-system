package tr.gov.kurum.identity.application.port.in;

public interface PurgeExpiredSessionsUseCase {

    /**
     * Deletes at most {@code batchSize} expired sessions (with their retired tokens) in one short transaction.
     *
     * @return number of deleted sessions; callers repeat while it equals {@code batchSize}
     */
    int purgeExpiredSessions(int batchSize);
}
