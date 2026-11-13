package tr.gov.kurum.customer.application.port.in;

/** Whitelist of sortable fields; clients can never sort by an arbitrary entity path. */
public enum CustomerSortField {
    EMAIL,
    LAST_NAME,
    REGISTERED_AT
}
