package tr.gov.kurum.common.infrastructure.adapter.out.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the JPA entity that stores an aggregate root. Only such entities may have a Spring Data repository;
 * child entities are saved through their root (checked by {@code ArchitectureTest}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PersistedAggregateRoot {
}
