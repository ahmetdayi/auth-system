package tr.gov.kurum.content.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Discount as a percentage with two decimals. BigDecimal, never double, for money-related values. */
public record DiscountRate(BigDecimal percent) {

    public DiscountRate {
        Objects.requireNonNull(percent, "percent");
        if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("discount must be between 0 and 100");
        }
        percent = percent.setScale(2, RoundingMode.UNNECESSARY);
    }
}
