package tr.gov.kurum.customer.application.port.in;

import java.util.Objects;
import tr.gov.kurum.common.query.PageQuery;
import tr.gov.kurum.common.query.SortDirection;

/**
 * @param emailContains optional case-insensitive filter (nullable)
 */
public record ListCustomersQuery(
        PageQuery page,
        CustomerSortField sortField,
        SortDirection direction,
        String emailContains) {

    private static final int MAX_FILTER_LENGTH = 100;

    public ListCustomersQuery {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(sortField, "sortField");
        Objects.requireNonNull(direction, "direction");
        if (emailContains != null) {
            emailContains = emailContains.isBlank() ? null : emailContains.trim();
        }
        if (emailContains != null && emailContains.length() > MAX_FILTER_LENGTH) {
            throw new IllegalArgumentException("filter is too long");
        }
    }
}
