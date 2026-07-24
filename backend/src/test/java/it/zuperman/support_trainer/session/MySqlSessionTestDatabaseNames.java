package it.zuperman.support_trainer.session;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Safety guard for opt-in MySQL session tests that recreate dedicated databases.
 * Validation is mandatory before JDBC URLs, CREATE/DROP DATABASE, or SQL interpolation.
 */
public final class MySqlSessionTestDatabaseNames {

    public static final String PREFIX = "support_trainer_session_test_";
    public static final String DEFAULT_EMPTY_SCHEMA = PREFIX + "empty";
    public static final String DEFAULT_FROM_V6_SCHEMA = PREFIX + "from_v6";

    private static final Pattern SAFE_NAME = Pattern.compile("^" + Pattern.quote(PREFIX) + "[a-z0-9_]+$");
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "mysql",
            "information_schema",
            "performance_schema",
            "sys"
    );

    private MySqlSessionTestDatabaseNames() {
    }

    public static String requireValid(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            throw new IllegalArgumentException("MySQL session test database name must not be null or blank");
        }
        String normalized = databaseName.trim();
        if (SYSTEM_SCHEMAS.contains(normalized.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "MySQL session test database name must not target a system schema: " + normalized
            );
        }
        if (!SAFE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "MySQL session test database name must start with '" + PREFIX
                            + "' and contain only lowercase letters, digits and underscores: " + normalized
            );
        }
        return normalized;
    }

    public static String[] requireDistinctPair(String firstDatabaseName, String secondDatabaseName) {
        String first = requireValid(firstDatabaseName);
        String second = requireValid(secondDatabaseName);
        if (Objects.equals(first, second)) {
            throw new IllegalArgumentException(
                    "MySQL session test database names must be distinct: " + first
            );
        }
        return new String[] {first, second};
    }

    public static String quoteIdentifier(String validatedDatabaseName) {
        String validated = requireValid(validatedDatabaseName);
        return "`" + validated + "`";
    }
}
