package tr.gov.kurum.content.domain.model;

import java.math.BigDecimal;

/**
 * The content context's own view of a reader's level. It is deliberately not the identity context's
 * membership enum: the web adapter translates authorities into this type (anti-corruption layer).
 */
public enum ReaderTier {

    NONE(new BigDecimal("0.00")),
    BRONZE(new BigDecimal("5.00")),
    SILVER(new BigDecimal("10.00")),
    GOLD(new BigDecimal("20.00"));

    private final BigDecimal discountPercent;

    ReaderTier(BigDecimal discountPercent) {
        this.discountPercent = discountPercent;
    }

    public boolean satisfies(ReaderTier required) {
        return this.ordinal() >= required.ordinal();
    }

    public DiscountRate discountRate() {
        return new DiscountRate(discountPercent);
    }
}
