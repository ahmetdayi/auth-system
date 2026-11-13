/**
 * Published language: the integration events bounded contexts exchange, grouped by the producing context.
 *
 * <p>A producer translates its own domain event into one of these in an outbound adapter; consumers listen to
 * these and never import the producer's code. That keeps modules free of cycles (rule 10) even when the
 * synchronous calls between them already point the other way (e.g. identity asks membership for a tier while
 * customer reacts to identity's registrations).</p>
 *
 * <p>Rules (checked by ArchitectureTest): only immutable records, only JDK and shared-kernel types, no
 * dependency on any module. A record here is a contract: change it compatibly (add fields) or add a new one;
 * events already in the outbox must still deserialize.</p>
 */
package tr.gov.kurum.contracts;
