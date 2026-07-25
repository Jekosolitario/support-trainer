package it.zuperman.support_trainer.security.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Test-only Hibernate inspector used to assert that readiness projection SQL
 * does not join subclass profile tables.
 */
public final class CapturingStatementInspector implements StatementInspector {

    private static final List<String> STATEMENTS = Collections.synchronizedList(new ArrayList<>());

    public CapturingStatementInspector() {
    }

    public static void clear() {
        STATEMENTS.clear();
    }

    public static List<String> statements() {
        return List.copyOf(STATEMENTS);
    }

    public static List<String> statementsContaining(String fragment) {
        String needle = fragment.toLowerCase(Locale.ROOT);
        return STATEMENTS.stream()
                .filter(sql -> sql.toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    @Override
    public String inspect(String sql) {
        if (sql != null) {
            STATEMENTS.add(sql);
        }
        return sql;
    }
}
