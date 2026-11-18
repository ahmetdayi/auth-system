package tr.gov.kurum.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Captures SQL executed on the current thread while counting is enabled. Used by N+1 guard tests.
 * Registered only in the integration-test context via {@link IntegrationTestBeans}.
 */
public final class CountingStatementInspector implements StatementInspector {

    private static final ThreadLocal<List<String>> CAPTURED = ThreadLocal.withInitial(ArrayList::new);
    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);

    @Override
    public String inspect(String sql) {
        if (ENABLED.get()) {
            CAPTURED.get().add(sql);
        }
        return sql;
    }

    public static void start() {
        CAPTURED.get().clear();
        ENABLED.set(true);
    }

    public static void stop() {
        ENABLED.set(false);
    }

    public static List<String> sql() {
        return List.copyOf(CAPTURED.get());
    }

    public static long countContaining(String fragment) {
        String needle = fragment.toLowerCase(Locale.ROOT);
        return sql().stream().filter(s -> s.toLowerCase(Locale.ROOT).contains(needle)).count();
    }

    public static long selectCount() {
        return sql().stream().filter(CountingStatementInspector::isSelect).count();
    }

    private static boolean isSelect(String sql) {
        String trimmed = sql.stripLeading().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("select");
    }
}
