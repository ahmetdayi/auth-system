package tr.gov.kurum.content.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tr.gov.kurum.common.domain.BusinessRuleException;

class ArticleTest {

    private static Article requiring(ReaderTier tier) {
        return Article.restore(new ArticleId(1), 0, "Başlık", "Özet", "Gövde", tier,
            Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void higher_tiers_satisfy_lower_requirements() {
        Article silver = requiring(ReaderTier.SILVER);

        assertThat(silver.isReadableBy(ReaderTier.GOLD)).isTrue();
        assertThat(silver.isReadableBy(ReaderTier.SILVER)).isTrue();
        assertThat(silver.isReadableBy(ReaderTier.BRONZE)).isFalse();
        assertThat(silver.isReadableBy(ReaderTier.NONE)).isFalse();
    }

    @Test
    void reading_without_the_required_tier_names_the_tier() {
        assertThatThrownBy(() -> requiring(ReaderTier.GOLD).requireReadableBy(ReaderTier.SILVER))
            .isInstanceOf(BusinessRuleException.class)
            .satisfies(e -> {
                BusinessRuleException rule = (BusinessRuleException) e;
                assertThat(rule.errorKey()).isEqualTo(ContentErrorKey.ARTICLE_REQUIRES_HIGHER_TIER);
                assertThat(rule.arguments()).containsExactly("GOLD");
            });
    }

    @Test
    void discount_rates_are_exact_decimals() {
        assertThat(ReaderTier.GOLD.discountRate().percent()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(ReaderTier.NONE.discountRate().percent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ReaderTier.SILVER.discountRate().percent().scale()).isEqualTo(2);
    }
}
