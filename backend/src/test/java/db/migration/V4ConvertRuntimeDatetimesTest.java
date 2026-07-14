package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

class V4ConvertRuntimeDatetimesTest {

    @Test
    void shouldConvertSummerRomeDateTimeToUtc() {
        Instant converted = V4__convert_runtime_datetimes_from_rome_to_utc.convertLegacyDateTime(
                LocalDateTime.parse("2026-07-13T17:30:00")
        );

        assertThat(converted).isEqualTo(Instant.parse("2026-07-13T15:30:00Z"));
    }

    @Test
    void shouldConvertWinterRomeDateTimeToUtc() {
        Instant converted = V4__convert_runtime_datetimes_from_rome_to_utc.convertLegacyDateTime(
                LocalDateTime.parse("2026-01-13T17:30:00")
        );

        assertThat(converted).isEqualTo(Instant.parse("2026-01-13T16:30:00Z"));
    }

    @Test
    void shouldPreserveMicrosecondsWithoutRounding() {
        Instant converted = V4__convert_runtime_datetimes_from_rome_to_utc.convertLegacyDateTime(
                LocalDateTime.parse("2026-07-13T17:30:00.123456")
        );

        assertThat(converted).isEqualTo(Instant.parse("2026-07-13T15:30:00.123456Z"));
    }

    @Test
    void shouldPreserveNullValues() {
        List<Instant> converted = V4__convert_runtime_datetimes_from_rome_to_utc.planValues(
                Arrays.asList((LocalDateTime) null)
        );

        assertThat(converted).containsExactly((Instant) null);
    }

    @Test
    void shouldAcceptAnEmptyDatabaseValueSet() {
        assertThat(V4__convert_runtime_datetimes_from_rome_to_utc.planValues(List.of())).isEmpty();
    }

    @Test
    void shouldFailOnSpringDstGap() {
        assertThatThrownBy(() -> V4__convert_runtime_datetimes_from_rome_to_utc.convertLegacyDateTime(
                LocalDateTime.parse("2026-03-29T02:30:00")
        ))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("DST gap");
    }

    @Test
    void shouldFailOnAutumnDstOverlap() {
        assertThatThrownBy(() -> V4__convert_runtime_datetimes_from_rome_to_utc.convertLegacyDateTime(
                LocalDateTime.parse("2026-10-25T02:30:00")
        ))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("DST overlap");
    }

    @Test
    void shouldExposeAStableExplicitChecksum() {
        V4__convert_runtime_datetimes_from_rome_to_utc first
                = new V4__convert_runtime_datetimes_from_rome_to_utc();
        V4__convert_runtime_datetimes_from_rome_to_utc second
                = new V4__convert_runtime_datetimes_from_rome_to_utc();

        assertThat(first.getChecksum())
                .isNotNull()
                .isEqualTo(second.getChecksum())
                .isNotEqualTo(-1886151667);
    }

    @Test
    void shouldLeaveSourceValuesUntouchedWhenCompletePreflightFails() {
        List<LocalDateTime> source = new ArrayList<>(List.of(
                LocalDateTime.parse("2026-07-13T17:30:00"),
                LocalDateTime.parse("2026-03-29T02:30:00")
        ));
        List<LocalDateTime> snapshot = List.copyOf(source);

        assertThatThrownBy(() -> V4__convert_runtime_datetimes_from_rome_to_utc.planValues(source))
                .isInstanceOf(FlywayException.class);
        assertThat(source).containsExactlyElementsOf(snapshot);
    }

    @Test
    void shouldAcceptTheExpectedMysqlDatetime6Schema() {
        assertThatCode(() -> V4__convert_runtime_datetimes_from_rome_to_utc.validateSchemaMetadata(
                V4__convert_runtime_datetimes_from_rome_to_utc.expectedSchemaMetadata()
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldReportDatetimeZeroPrecisionBeforeDml() {
        Map<String, V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata> schema = mutableExpectedSchema();
        replaceColumn(schema, "users", "created_at", column("datetime", 0, false));

        assertThatThrownBy(() -> V4__convert_runtime_datetimes_from_rome_to_utc.validateSchemaMetadata(schema))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("users.created_at precision is 0 (expected 6)");
    }

    @Test
    void shouldReportAnIncorrectTemporalType() {
        Map<String, V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata> schema = mutableExpectedSchema();
        replaceColumn(schema, "users", "created_at", column("varchar", null, false));

        assertThatThrownBy(() -> V4__convert_runtime_datetimes_from_rome_to_utc.validateSchemaMetadata(schema))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("users.created_at type is varchar (expected datetime)");
    }

    @Test
    void shouldReportAMissingTemporalColumn() {
        Map<String, V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata> schema = mutableExpectedSchema();
        removeColumn(schema, "users", "created_at");

        assertThatThrownBy(() -> V4__convert_runtime_datetimes_from_rome_to_utc.validateSchemaMetadata(schema))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("users.created_at is absent");
    }

    @Test
    void shouldReportAMissingRuntimeTable() {
        Map<String, V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata> schema = mutableExpectedSchema();
        schema.remove("users");

        assertThatThrownBy(() -> V4__convert_runtime_datetimes_from_rome_to_utc.validateSchemaMetadata(schema))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("table users is absent");
    }

    @Test
    void shouldReportUnexpectedNullabilityAndAllOtherDivergences() {
        Map<String, V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata> schema = mutableExpectedSchema();
        replaceColumn(schema, "users", "created_at", column("datetime", 0, true));

        assertThatThrownBy(() -> V4__convert_runtime_datetimes_from_rome_to_utc.validateSchemaMetadata(schema))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("users.created_at precision is 0 (expected 6)")
                .hasMessageContaining("users.created_at nullability is NULL (expected NOT NULL)");
    }

    @Test
    void shouldUseSelectedDatabaseWhenCatalogIsUnavailable() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.getCatalog()).thenReturn(null);
        when(connection.prepareStatement("SELECT DATABASE()")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("synthetic_schema");

        assertThat(V4__convert_runtime_datetimes_from_rome_to_utc.resolveCurrentSchema(connection))
                .isEqualTo("synthetic_schema");
    }

    @Test
    void shouldFailExplicitlyWhenCurrentDatabaseCannotBeDetermined() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.getCatalog()).thenReturn(" ");
        when(connection.prepareStatement("SELECT DATABASE()")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn(null);

        assertThatThrownBy(() -> V4__convert_runtime_datetimes_from_rome_to_utc.resolveCurrentSchema(connection))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("cannot determine the current MySQL database");
    }

    @Test
    void shouldNotPrepareAnyUpdateWhenSchemaResolutionFails() throws Exception {
        Context context = mock(Context.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn(null);
        when(connection.prepareStatement("SELECT DATABASE()")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        V4__convert_runtime_datetimes_from_rome_to_utc migration
                = new V4__convert_runtime_datetimes_from_rome_to_utc();

        assertThatThrownBy(() -> migration.migrate(context))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("cannot determine");
        verify(connection, never()).prepareStatement(argThat(
                sql -> sql != null && sql.stripLeading().toUpperCase().startsWith("UPDATE")
        ));
    }

    private static Map<String, V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata>
            mutableExpectedSchema() {
        return new LinkedHashMap<>(V4__convert_runtime_datetimes_from_rome_to_utc.expectedSchemaMetadata());
    }

    private static V4__convert_runtime_datetimes_from_rome_to_utc.ColumnMetadata column(
            String type,
            Integer precision,
            boolean nullable
    ) {
        return new V4__convert_runtime_datetimes_from_rome_to_utc.ColumnMetadata(type, precision, nullable);
    }

    private static void replaceColumn(
            Map<String, V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata> schema,
            String table,
            String column,
            V4__convert_runtime_datetimes_from_rome_to_utc.ColumnMetadata replacement
    ) {
        V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata current = schema.get(table);
        Map<String, V4__convert_runtime_datetimes_from_rome_to_utc.ColumnMetadata> columns
                = new LinkedHashMap<>(current.columns());
        columns.put(column, replacement);
        schema.put(table, new V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata(
                current.engine(), Map.copyOf(columns)
        ));
    }

    private static void removeColumn(
            Map<String, V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata> schema,
            String table,
            String column
    ) {
        V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata current = schema.get(table);
        Map<String, V4__convert_runtime_datetimes_from_rome_to_utc.ColumnMetadata> columns
                = new LinkedHashMap<>(current.columns());
        columns.remove(column);
        schema.put(table, new V4__convert_runtime_datetimes_from_rome_to_utc.TableMetadata(
                current.engine(), Map.copyOf(columns)
        ));
    }
}
