package tr.gov.kurum.customer.infrastructure.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tr.gov.kurum.common.query.PageQuery;
import tr.gov.kurum.common.query.SortDirection;
import tr.gov.kurum.common.web.PageResponse;
import tr.gov.kurum.common.domain.PermissionCodes;
import tr.gov.kurum.customer.application.port.in.CustomerDirectoryEntry;
import tr.gov.kurum.customer.application.port.in.CustomerDirectoryUseCase;
import tr.gov.kurum.customer.application.port.in.CustomerSortField;
import tr.gov.kurum.customer.application.port.in.ListCustomersQuery;

/**
 * The admin's customer list. Only this context's data: account status comes from
 * {@code /api/admin/accounts?ids=} and membership from {@code /api/admin/memberships?ids=}; the admin screen
 * puts them side by side (UI composition).
 */
@RestController
@RequestMapping("/api/admin")
class CustomerDirectoryController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final CustomerDirectoryUseCase directory;

    CustomerDirectoryController(CustomerDirectoryUseCase directory) {
        this.directory = directory;
    }

    /** Sort fields are an enum: an unknown value is a 400, never a column name passed to the database. */
    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('TYPE_ADMIN') and hasAuthority('" + PermissionCodes.CUSTOMER_READ + "')")
    PageResponse<CustomerDirectoryEntry> listCustomers(
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(PageQuery.MAX_SIZE) int size,
            @RequestParam(defaultValue = "REGISTERED_AT") CustomerSortField sort,
            @RequestParam(defaultValue = "DESC") SortDirection direction,
            @RequestParam(required = false) @Size(max = 100) String email) {
        return PageResponse.of(directory.listCustomers(
            new ListCustomersQuery(new PageQuery(page, size), sort, direction, email)));
    }
}
