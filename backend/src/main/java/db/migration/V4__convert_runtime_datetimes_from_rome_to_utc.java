package db.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V4__convert_runtime_datetimes_from_rome_to_utc extends BaseJavaMigration {

    private static final System.Logger LOGGER = System.getLogger(
            V4__convert_runtime_datetimes_from_rome_to_utc.class.getName()
    );
    private static final ZoneId LEGACY_ZONE = ZoneId.of("Europe/Rome");
    private static final ZoneRules LEGACY_ZONE_RULES = LEGACY_ZONE.getRules();
    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private static final String CHECKSUM_CONTRACT = "v4|legacy=Europe/Rome|target=UTC|precision=micros|"
            + "schema=information_schema.COLUMNS:DATETIME_PRECISION,nullability|"
            + "schema-resolution=connection-catalog,SELECT-DATABASE|"
            + "users:created_at,updated_at|"
            + "professional_profiles:created_at,updated_at|"
            + "client_profiles:created_at,updated_at|"
            + "professional_client_links:created_at,updated_at|"
            + "invite_codes:expires_at,used_at,created_at,updated_at|"
            + "email_verification_tokens:expires_at,used_at,created_at|"
            + "availability_slots:start_date_time,end_date_time,created_at,updated_at|"
            + "booking_requests:created_at,updated_at|"
            + "booking_request_items:created_at,updated_at";

    private static final Map<String, Boolean> EXPECTED_NULLABILITY = Map.ofEntries(
            Map.entry("users.created_at", false),
            Map.entry("users.updated_at", false),
            Map.entry("professional_profiles.created_at", false),
            Map.entry("professional_profiles.updated_at", false),
            Map.entry("client_profiles.created_at", false),
            Map.entry("client_profiles.updated_at", false),
            Map.entry("professional_client_links.created_at", false),
            Map.entry("professional_client_links.updated_at", false),
            Map.entry("invite_codes.expires_at", false),
            Map.entry("invite_codes.used_at", true),
            Map.entry("invite_codes.created_at", false),
            Map.entry("invite_codes.updated_at", false),
            Map.entry("email_verification_tokens.expires_at", false),
            Map.entry("email_verification_tokens.used_at", true),
            Map.entry("email_verification_tokens.created_at", false),
            Map.entry("availability_slots.start_date_time", false),
            Map.entry("availability_slots.end_date_time", false),
            Map.entry("availability_slots.created_at", false),
            Map.entry("availability_slots.updated_at", false),
            Map.entry("booking_requests.created_at", false),
            Map.entry("booking_requests.updated_at", false),
            Map.entry("booking_request_items.created_at", false),
            Map.entry("booking_request_items.updated_at", false)
    );

    private static final List<TableSpec> TABLES = List.of(
            new TableSpec(
                    "users",
                    List.of("created_at", "updated_at"),
                    "SELECT id, created_at, updated_at FROM users ORDER BY id FOR UPDATE",
                    "UPDATE users SET created_at = ?, updated_at = ? WHERE id = ?"
            ),
            new TableSpec(
                    "professional_profiles",
                    List.of("created_at", "updated_at"),
                    "SELECT id, created_at, updated_at FROM professional_profiles ORDER BY id FOR UPDATE",
                    "UPDATE professional_profiles SET created_at = ?, updated_at = ? WHERE id = ?"
            ),
            new TableSpec(
                    "client_profiles",
                    List.of("created_at", "updated_at"),
                    "SELECT id, created_at, updated_at FROM client_profiles ORDER BY id FOR UPDATE",
                    "UPDATE client_profiles SET created_at = ?, updated_at = ? WHERE id = ?"
            ),
            new TableSpec(
                    "professional_client_links",
                    List.of("created_at", "updated_at"),
                    "SELECT id, created_at, updated_at FROM professional_client_links ORDER BY id FOR UPDATE",
                    "UPDATE professional_client_links SET created_at = ?, updated_at = ? WHERE id = ?"
            ),
            new TableSpec(
                    "invite_codes",
                    List.of("expires_at", "used_at", "created_at", "updated_at"),
                    "SELECT id, expires_at, used_at, created_at, updated_at FROM invite_codes ORDER BY id FOR UPDATE",
                    "UPDATE invite_codes SET expires_at = ?, used_at = ?, created_at = ?, updated_at = ? WHERE id = ?"
            ),
            new TableSpec(
                    "email_verification_tokens",
                    List.of("expires_at", "used_at", "created_at"),
                    "SELECT id, expires_at, used_at, created_at FROM email_verification_tokens ORDER BY id FOR UPDATE",
                    "UPDATE email_verification_tokens SET expires_at = ?, used_at = ?, created_at = ? WHERE id = ?"
            ),
            new TableSpec(
                    "availability_slots",
                    List.of("start_date_time", "end_date_time", "created_at", "updated_at"),
                    "SELECT id, start_date_time, end_date_time, created_at, updated_at "
                            + "FROM availability_slots ORDER BY id FOR UPDATE",
                    "UPDATE availability_slots SET start_date_time = ?, end_date_time = ?, "
                            + "created_at = ?, updated_at = ? WHERE id = ?"
            ),
            new TableSpec(
                    "booking_requests",
                    List.of("created_at", "updated_at"),
                    "SELECT id, created_at, updated_at FROM booking_requests ORDER BY id FOR UPDATE",
                    "UPDATE booking_requests SET created_at = ?, updated_at = ? WHERE id = ?"
            ),
            new TableSpec(
                    "booking_request_items",
                    List.of("created_at", "updated_at"),
                    "SELECT id, created_at, updated_at FROM booking_request_items ORDER BY id FOR UPDATE",
                    "UPDATE booking_request_items SET created_at = ?, updated_at = ? WHERE id = ?"
            )
    );

    @Override
    public Integer getChecksum() {
        CRC32 crc32 = new CRC32();
        crc32.update(CHECKSUM_CONTRACT.getBytes(StandardCharsets.UTF_8));
        return (int) crc32.getValue();
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        verifyExpectedSchema(connection);

        List<TablePlan> plans = new ArrayList<>(TABLES.size());
        MessageDigest sourceDigest = sha256();
        MessageDigest expectedUtcDigest = sha256();
        long sourceValueCount = 0;
        long sourceNullCount = 0;

        for (TableSpec table : TABLES) {
            TablePlan plan = preflightTable(connection, table, sourceDigest, expectedUtcDigest);
            plans.add(plan);
            sourceValueCount += plan.nonNullCount();
            sourceNullCount += plan.nullCount();
        }

        String sourceDigestHex = HexFormat.of().formatHex(sourceDigest.digest());
        String expectedUtcDigestHex = HexFormat.of().formatHex(expectedUtcDigest.digest());
        long rowCount = plans.stream().mapToLong(plan -> plan.rows().size()).sum();
        LOGGER.log(
                System.Logger.Level.INFO,
                "V4 preflight completed: tables={0}, rows={1}, values={2}, nulls={3}, sourceDigest={4}, expectedUtcDigest={5}",
                TABLES.size(), rowCount, sourceValueCount, sourceNullCount, sourceDigestHex, expectedUtcDigestHex
        );

        for (TablePlan plan : plans) {
            applyPlan(connection, plan);
        }

        VerificationSummary verification = verifyPersistedUtc(connection, plans);
        if (!expectedUtcDigestHex.equals(verification.digest())) {
            throw new FlywayException("V4 UTC digest verification failed");
        }
        if (sourceValueCount != verification.nonNullCount() || sourceNullCount != verification.nullCount()) {
            throw new FlywayException("V4 value or null count verification failed");
        }

        LOGGER.log(
                System.Logger.Level.INFO,
                "V4 UTC verification completed: rows={0}, values={1}, nulls={2}, utcDigest={3}",
                verification.rowCount(), verification.nonNullCount(), verification.nullCount(), verification.digest()
        );
    }

    static Instant convertLegacyDateTime(LocalDateTime legacyValue) {
        Objects.requireNonNull(legacyValue, "legacyValue must not be null");
        if (legacyValue.getNano() % 1_000 != 0) {
            throw new FlywayException("V4 source precision exceeds DATETIME(6)");
        }

        List<ZoneOffset> validOffsets = LEGACY_ZONE_RULES.getValidOffsets(legacyValue);
        if (validOffsets.isEmpty()) {
            throw new FlywayException("V4 found a legacy datetime in a Europe/Rome DST gap");
        }
        if (validOffsets.size() != 1) {
            throw new FlywayException("V4 found a legacy datetime in a Europe/Rome DST overlap");
        }

        return legacyValue.toInstant(validOffsets.getFirst()).truncatedTo(ChronoUnit.MICROS);
    }

    static List<Instant> planValues(List<LocalDateTime> legacyValues) {
        List<Instant> converted = new ArrayList<>(legacyValues.size());
        for (LocalDateTime value : legacyValues) {
            converted.add(value == null ? null : convertLegacyDateTime(value));
        }
        return Collections.unmodifiableList(converted);
    }

    static void verifyExpectedSchema(Connection connection) throws SQLException {
        String catalog = resolveCurrentSchema(connection);
        Map<String, TableMetadata> actualSchema = loadSchemaMetadata(connection, catalog);
        validateSchemaMetadata(actualSchema);
    }

    static String resolveCurrentSchema(Connection connection) throws SQLException {
        String catalog = connection.getCatalog();
        if (catalog != null && !catalog.isBlank()) {
            return catalog;
        }

        try (PreparedStatement statement = connection.prepareStatement("SELECT DATABASE()");
                ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                String selectedDatabase = resultSet.getString(1);
                if (selectedDatabase != null && !selectedDatabase.isBlank()) {
                    return selectedDatabase;
                }
            }
        }
        throw new FlywayException("V4 cannot determine the current MySQL database");
    }

    static void validateSchemaMetadata(Map<String, TableMetadata> actualSchema) {
        List<String> divergences = new ArrayList<>();
        for (TableSpec table : TABLES) {
            TableMetadata actualTable = actualSchema.get(table.name());
            if (actualTable == null) {
                divergences.add("table " + table.name() + " is absent");
                continue;
            }
            if (!"InnoDB".equalsIgnoreCase(actualTable.engine())) {
                divergences.add("table " + table.name() + " engine is "
                        + printable(actualTable.engine()) + " (expected InnoDB)");
            }
            ColumnMetadata id = actualTable.columns().get("id");
            if (id == null) {
                divergences.add(table.name() + ".id is absent");
            } else if (!"bigint".equalsIgnoreCase(id.dataType())) {
                divergences.add(table.name() + ".id type is "
                        + printable(id.dataType()) + " (expected bigint)");
            }
            for (String column : table.columns()) {
                String qualifiedName = table.name() + '.' + column;
                ColumnMetadata actual = actualTable.columns().get(column);
                if (actual == null) {
                    divergences.add(qualifiedName + " is absent");
                    continue;
                }
                if (!"datetime".equalsIgnoreCase(actual.dataType())) {
                    divergences.add(qualifiedName + " type is "
                            + printable(actual.dataType()) + " (expected datetime)");
                } else if (actual.datetimePrecision() == null) {
                    divergences.add(qualifiedName + " precision is unavailable (expected 6)");
                } else if (actual.datetimePrecision() != 6) {
                    divergences.add(qualifiedName + " precision is "
                            + actual.datetimePrecision() + " (expected 6)");
                }
                Boolean expectedNullable = EXPECTED_NULLABILITY.get(qualifiedName);
                if (actual.nullable() == null) {
                    divergences.add(qualifiedName + " nullability is unavailable (expected "
                            + nullabilityLabel(expectedNullable) + ')');
                } else if (!actual.nullable().equals(expectedNullable)) {
                    divergences.add(qualifiedName + " nullability is "
                            + nullabilityLabel(actual.nullable()) + " (expected "
                            + nullabilityLabel(expectedNullable) + ')');
                }
            }
        }
        if (!divergences.isEmpty()) {
            throw new FlywayException("V4 schema precondition failed: " + String.join("; ", divergences));
        }
    }

    static Map<String, TableMetadata> expectedSchemaMetadata() {
        Map<String, TableMetadata> expected = new LinkedHashMap<>();
        for (TableSpec table : TABLES) {
            Map<String, ColumnMetadata> columns = new LinkedHashMap<>();
            columns.put("id", new ColumnMetadata("bigint", null, false));
            for (String column : table.columns()) {
                String qualifiedName = table.name() + '.' + column;
                columns.put(column, new ColumnMetadata(
                        "datetime",
                        6,
                        EXPECTED_NULLABILITY.get(qualifiedName)
                ));
            }
            expected.put(table.name(), new TableMetadata("InnoDB", Map.copyOf(columns)));
        }
        return Map.copyOf(expected);
    }

    private static Map<String, TableMetadata> loadSchemaMetadata(Connection connection, String catalog)
            throws SQLException {
        Map<String, TableMetadata> schema = new LinkedHashMap<>();
        for (TableSpec table : TABLES) {
            String engine = loadEngine(connection, catalog, table.name());
            if (engine != null) {
                schema.put(table.name(), new TableMetadata(
                        engine,
                        loadColumns(connection, catalog, table.name())
                ));
            }
        }
        return Map.copyOf(schema);
    }

    private static String loadEngine(Connection connection, String catalog, String table) throws SQLException {
        String sql = "SELECT ENGINE FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = ? AND BINARY TABLE_NAME = BINARY ? AND TABLE_TYPE = 'BASE TABLE'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, catalog);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("ENGINE");
                }
            }
        }
        return null;
    }

    private static Map<String, ColumnMetadata> loadColumns(
            Connection connection,
            String catalog,
            String table
    ) throws SQLException {
        Map<String, ColumnMetadata> columns = new LinkedHashMap<>();
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, DATETIME_PRECISION, IS_NULLABLE "
                + "FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = ? AND BINARY TABLE_NAME = BINARY ? ORDER BY ORDINAL_POSITION";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, catalog);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String name = resultSet.getString("COLUMN_NAME");
                    String dataType = resultSet.getString("DATA_TYPE");
                    Object precisionValue = resultSet.getObject("DATETIME_PRECISION");
                    Integer datetimePrecision = precisionValue instanceof Number number
                            ? number.intValue()
                            : null;
                    String isNullable = resultSet.getString("IS_NULLABLE");
                    Boolean nullable = "YES".equalsIgnoreCase(isNullable)
                            ? Boolean.TRUE
                            : "NO".equalsIgnoreCase(isNullable) ? Boolean.FALSE : null;
                    columns.put(name, new ColumnMetadata(
                            dataType == null ? null : dataType.toLowerCase(Locale.ROOT),
                            datetimePrecision,
                            nullable
                    ));
                }
            }
        }
        return Map.copyOf(columns);
    }

    private static String printable(String value) {
        return value == null || value.isBlank() ? "unavailable" : value;
    }

    private static String nullabilityLabel(Boolean nullable) {
        if (nullable == null) {
            return "unavailable";
        }
        return nullable ? "NULL" : "NOT NULL";
    }

    private static TablePlan preflightTable(
            Connection connection,
            TableSpec table,
            MessageDigest sourceDigest,
            MessageDigest expectedUtcDigest
    ) throws SQLException {
        List<RowPlan> rows = new ArrayList<>();
        long nonNullCount = 0;
        long nullCount = 0;
        int rowOrdinal = 0;

        try (PreparedStatement statement = connection.prepareStatement(table.selectSql());
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                long id = resultSet.getLong("id");
                if (resultSet.wasNull()) {
                    throw new FlywayException("V4 found a null runtime primary key");
                }

                List<Instant> targetValues = new ArrayList<>(table.columns().size());
                for (String column : table.columns()) {
                    LocalDateTime sourceValue = resultSet.getObject(column, LocalDateTime.class);
                    Instant targetValue = sourceValue == null ? null : convertLegacyDateTime(sourceValue);
                    updateDigest(sourceDigest, table.name(), column, rowOrdinal, sourceValue);
                    updateDigest(
                            expectedUtcDigest,
                            table.name(),
                            column,
                            rowOrdinal,
                            targetValue == null ? null : LocalDateTime.ofInstant(targetValue, UTC)
                    );
                    targetValues.add(targetValue);
                    if (sourceValue == null) {
                        nullCount++;
                    } else {
                        nonNullCount++;
                    }
                }
                rows.add(new RowPlan(id, Collections.unmodifiableList(targetValues)));
                rowOrdinal++;
            }
        }

        return new TablePlan(table, List.copyOf(rows), nonNullCount, nullCount);
    }

    private static void applyPlan(Connection connection, TablePlan plan) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(plan.table().updateSql())) {
            for (RowPlan row : plan.rows()) {
                int parameter = 1;
                for (Instant targetValue : row.targetValues()) {
                    if (targetValue == null) {
                        statement.setNull(parameter++, Types.TIMESTAMP);
                    } else {
                        statement.setObject(parameter++, LocalDateTime.ofInstant(targetValue, UTC));
                    }
                }
                statement.setLong(parameter, row.id());
                if (statement.executeUpdate() != 1) {
                    throw new FlywayException("V4 did not update exactly one expected runtime row");
                }
            }
        }
    }

    private static VerificationSummary verifyPersistedUtc(
            Connection connection,
            List<TablePlan> plans
    ) throws SQLException {
        MessageDigest digest = sha256();
        long rowCount = 0;
        long nonNullCount = 0;
        long nullCount = 0;

        for (TablePlan plan : plans) {
            int rowOrdinal = 0;
            try (PreparedStatement statement = connection.prepareStatement(plan.table().selectSql());
                    ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (rowOrdinal >= plan.rows().size()) {
                        throw new FlywayException("V4 post-update row count increased unexpectedly");
                    }
                    RowPlan expectedRow = plan.rows().get(rowOrdinal);
                    long actualId = resultSet.getLong("id");
                    if (resultSet.wasNull() || actualId != expectedRow.id()) {
                        throw new FlywayException("V4 post-update row identity verification failed");
                    }

                    for (int columnIndex = 0; columnIndex < plan.table().columns().size(); columnIndex++) {
                        String column = plan.table().columns().get(columnIndex);
                        LocalDateTime actual = resultSet.getObject(column, LocalDateTime.class);
                        Instant expectedInstant = expectedRow.targetValues().get(columnIndex);
                        LocalDateTime expected = expectedInstant == null
                                ? null
                                : LocalDateTime.ofInstant(expectedInstant, UTC);
                        if (!Objects.equals(expected, actual)) {
                            throw new FlywayException("V4 post-update UTC value verification failed");
                        }
                        if (actual != null && actual.getNano() % 1_000 != 0) {
                            throw new FlywayException("V4 post-update microsecond verification failed");
                        }
                        updateDigest(digest, plan.table().name(), column, rowOrdinal, actual);
                        if (actual == null) {
                            nullCount++;
                        } else {
                            nonNullCount++;
                        }
                    }
                    rowOrdinal++;
                    rowCount++;
                }
            }
            if (rowOrdinal != plan.rows().size()) {
                throw new FlywayException("V4 post-update row count decreased unexpectedly");
            }
        }

        return new VerificationSummary(
                rowCount,
                nonNullCount,
                nullCount,
                HexFormat.of().formatHex(digest.digest())
        );
    }

    private static void updateDigest(
            MessageDigest digest,
            String table,
            String column,
            int rowOrdinal,
            LocalDateTime value
    ) {
        String canonical = table + '|' + column + '|' + rowOrdinal + '|'
                + (value == null ? "<null>" : value.toString()) + '\n';
        digest.update(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private record TableSpec(String name, List<String> columns, String selectSql, String updateSql) {
    }

    record ColumnMetadata(String dataType, Integer datetimePrecision, Boolean nullable) {
    }

    record TableMetadata(String engine, Map<String, ColumnMetadata> columns) {
    }

    private record RowPlan(long id, List<Instant> targetValues) {
    }

    private record TablePlan(TableSpec table, List<RowPlan> rows, long nonNullCount, long nullCount) {
    }

    private record VerificationSummary(long rowCount, long nonNullCount, long nullCount, String digest) {
    }
}
