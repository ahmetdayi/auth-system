package tr.gov.kurum.content.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.gov.kurum.content.application.port.out.ArticleRepository;
import tr.gov.kurum.content.domain.model.Article;
import tr.gov.kurum.content.domain.model.ArticleId;
import tr.gov.kurum.content.domain.model.ReaderTier;

@Component
@RequiredArgsConstructor
class ArticlePersistenceAdapter implements ArticleRepository {

    private final ArticleJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<Article> findLatest(int limit) {
        return repository.findAllByOrderByPublishedAtDesc(Limit.of(limit)).stream()
            .map(ArticlePersistenceAdapter::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Article> findLatestRequiring(ReaderTier tier, int limit) {
        return repository.findByRequiredTierOrderByPublishedAtDesc(tier, Limit.of(limit)).stream()
            .map(ArticlePersistenceAdapter::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Article> findById(ArticleId id) {
        return repository.findById(id.value()).map(ArticlePersistenceAdapter::toDomain);
    }

    private static Article toDomain(ArticleJpaEntity entity) {
        return Article.restore(new ArticleId(entity.getId()), entity.getVersion(), entity.getTitle(),
            entity.getSummary(), entity.getBody(), entity.getRequiredTier(), entity.getPublishedAt());
    }
}
