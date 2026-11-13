package tr.gov.kurum.common.domain;

/**
 * A domain event other bounded contexts react to. It is stored in the transactional outbox together with
 * the change that caused it and delivered afterwards, so it is never lost (rule 6.2).
 *
 * <p>Implementations are the published language between contexts: records with shared-kernel and JDK types
 * only, so that consumers do not depend on the producer's model, and so they survive a JSON round trip.</p>
 */
public interface IntegrationEvent extends DomainEvent {
}
