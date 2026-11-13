package tr.gov.kurum.content.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.BaseJpaEntity;
import tr.gov.kurum.common.infrastructure.adapter.out.persistence.PersistedAggregateRoot;
import tr.gov.kurum.content.domain.model.ReaderTier;

@Entity
@Table(name = "articles")
@PersistedAggregateRoot
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ArticleJpaEntity extends BaseJpaEntity {

    @Id
    private Long id;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "summary", nullable = false, length = 300)
    private String summary;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_tier", nullable = false, length = 20)
    private ReaderTier requiredTier;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
}
