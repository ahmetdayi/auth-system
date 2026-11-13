package tr.gov.kurum.content.application.port.in;

import java.math.BigDecimal;
import tr.gov.kurum.content.domain.model.ReaderTier;

public record DiscountView(ReaderTier readerTier, BigDecimal discountPercent) {
}
