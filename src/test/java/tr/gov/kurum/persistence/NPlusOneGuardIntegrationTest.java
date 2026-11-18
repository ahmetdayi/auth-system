package tr.gov.kurum.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tr.gov.kurum.common.domain.AccountId;
import tr.gov.kurum.common.query.PageQuery;
import tr.gov.kurum.common.query.PageResult;
import tr.gov.kurum.common.query.SortDirection;
import tr.gov.kurum.customer.application.port.in.CustomerDirectoryEntry;
import tr.gov.kurum.customer.application.port.in.CustomerDirectoryUseCase;
import tr.gov.kurum.customer.application.port.in.CustomerSortField;
import tr.gov.kurum.customer.application.port.in.ListCustomersQuery;
import tr.gov.kurum.identity.application.port.in.SupportGrantUseCase;
import tr.gov.kurum.identity.application.port.in.SupportGrantView;
import tr.gov.kurum.support.CountingStatementInspector;
import tr.gov.kurum.support.IntegrationTestBase;
import tr.gov.kurum.support.TestData.Account;

/**
 * Guards against lazy-collection N+1 on the hot list paths. Seed many rows, run the use case once,
 * assert SQL stays within a fixed budget (BatchSize / projection / batched summaries).
 */
class NPlusOneGuardIntegrationTest extends IntegrationTestBase {

    private static final int GRANT_ROWS = 20;
    private static final int EXTENSIONS_PER_GRANT = 2;
    private static final int DIRECTORY_CUSTOMERS = 25;
    private static final int ADDRESSES_PER_CUSTOMER = 2;

    @Autowired
    private SupportGrantUseCase supportGrants;

    @Autowired
    private CustomerDirectoryUseCase customerDirectory;

    @AfterEach
    void stopCounting() {
        CountingStatementInspector.stop();
    }

    @Test
    void support_grant_mine_loads_extensions_and_emails_without_per_row_queries() {
        Account agent = data.support();
        for (int i = 0; i < GRANT_ROWS; i++) {
            Account customer = data.customer();
            data.endedGrantWithExtensions(agent, customer, EXTENSIONS_PER_GRANT);
        }

        CountingStatementInspector.start();
        List<SupportGrantView> views = supportGrants.mine(new AccountId(agent.id()));
        CountingStatementInspector.stop();

        assertThat(views).hasSize(GRANT_ROWS);
        assertThat(views).allSatisfy(view -> assertThat(view.extensions()).hasSize(EXTENSIONS_PER_GRANT));
        assertThat(views).allSatisfy(view -> {
            assertThat(view.agentEmail()).isEqualTo(agent.email());
            assertThat(view.customerEmail()).isNotBlank();
        });

        // 1 grants list + ≤1 batched extensions + 1 account summaries (+ small Hibernate headroom).
        assertThat(CountingStatementInspector.selectCount())
            .as(sqlDump())
            .isLessThanOrEqualTo(5);
        assertThat(CountingStatementInspector.countContaining("support_grant_extensions"))
            .as(sqlDump())
            .isLessThanOrEqualTo(1);
        assertThat(CountingStatementInspector.countContaining("support_grant_extensions"))
            .as("classic N+1 would hit extensions once per grant")
            .isLessThan(GRANT_ROWS);
    }

    @Test
    void customer_directory_does_not_touch_addresses() {
        String marker = "n1dir-" + Long.toString(System.nanoTime(), 36);
        for (int i = 0; i < DIRECTORY_CUSTOMERS; i++) {
            Account customer = data.customerWithEmailPrefix(marker);
            data.addAddresses(customer, ADDRESSES_PER_CUSTOMER);
        }

        CountingStatementInspector.start();
        PageResult<CustomerDirectoryEntry> page = customerDirectory.listCustomers(
            new ListCustomersQuery(new PageQuery(0, 50), CustomerSortField.EMAIL, SortDirection.ASC, marker));
        CountingStatementInspector.stop();

        assertThat(page.content()).hasSize(DIRECTORY_CUSTOMERS);
        assertThat(CountingStatementInspector.countContaining("customer_addresses"))
            .as(sqlDump())
            .isZero();
        // content query + count query
        assertThat(CountingStatementInspector.selectCount())
            .as(sqlDump())
            .isLessThanOrEqualTo(3);
    }

    private static String sqlDump() {
        return "SQL captured:\n" + String.join("\n---\n", CountingStatementInspector.sql());
    }
}
