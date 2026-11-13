package tr.gov.kurum.content.infrastructure.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import tr.gov.kurum.content.domain.model.ReaderTier;

interface ArticleJpaRepository extends JpaRepository<ArticleJpaEntity, Long> {

    List<ArticleJpaEntity> findAllByOrderByPublishedAtDesc(Limit limit);

    List<ArticleJpaEntity> findByRequiredTierOrderByPublishedAtDesc(ReaderTier requiredTier, Limit limit);
}
