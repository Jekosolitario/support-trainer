package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Adds immutable booking snapshots after a complete preflight of legacy rows.
 *
 * <p>The legacy display names are reconstructed from the profile values available at migration time;
 * they do not prove the names originally used when the booking was created.</p>
 */
public class V6__add_booking_historical_snapshots extends BaseJavaMigration {

    private static final int DISPLAY_NAME_LENGTH = 201;
    private static final String CHECKSUM_CONTRACT = "v6|booking-history|display-names=201|"
            + "scheduled-instants=utc-micros|timeline=updated-at-by-terminal-status|"
            + "preflight=profiles,slots,items,positive-intervals|no-placeholders";

    @Override
    public Integer getChecksum() {
        CRC32 crc32 = new CRC32();
        crc32.update(CHECKSUM_CONTRACT.getBytes(StandardCharsets.UTF_8));
        return (int) crc32.getValue();
    }

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        MigrationPlan plan = preflight(connection);

        addColumns(connection);
        applyBookingSnapshots(connection, plan.bookingRequests());
        applyItemSnapshots(connection, plan.bookingItems());
        enforceRequiredSnapshots(connection);
        verifyPersistedSnapshots(connection, plan);
    }

    private static MigrationPlan preflight(Connection connection) throws SQLException {
        Map<Long, BookingRequestPlan> bookingRequests = loadBookingRequestPlans(connection);
        List<BookingItemPlan> bookingItems = loadBookingItemPlans(connection, bookingRequests);
        verifyEveryBookingHasAnItem(bookingRequests, bookingItems);
        return new MigrationPlan(List.copyOf(bookingRequests.values()), List.copyOf(bookingItems));
    }

    private static Map<Long, BookingRequestPlan> loadBookingRequestPlans(Connection connection) throws SQLException {
        String sql = "SELECT booking.id, booking.status, booking.updated_at, "
                + "client_profile.id AS client_profile_id, client_user.first_name AS client_first_name, "
                + "client_user.last_name AS client_last_name, professional_profile.id AS professional_profile_id, "
                + "professional_user.first_name AS professional_first_name, "
                + "professional_user.last_name AS professional_last_name "
                + "FROM booking_requests booking "
                + "LEFT JOIN client_profiles client_profile ON client_profile.id = booking.client_id "
                + "LEFT JOIN users client_user ON client_user.id = client_profile.id "
                + "LEFT JOIN professional_profiles professional_profile ON professional_profile.id = booking.professional_id "
                + "LEFT JOIN users professional_user ON professional_user.id = professional_profile.id "
                + "ORDER BY booking.id";
        Map<Long, BookingRequestPlan> plans = new LinkedHashMap<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                long id = requiredId(resultSet, "id", "booking request");
                requirePresent(resultSet, "client_profile_id", "client profile", id);
                requirePresent(resultSet, "professional_profile_id", "professional profile", id);

                String clientDisplayName = displayName(
                        resultSet.getString("client_first_name"),
                        resultSet.getString("client_last_name"),
                        "client",
                        id
                );
                String professionalDisplayName = displayName(
                        resultSet.getString("professional_first_name"),
                        resultSet.getString("professional_last_name"),
                        "professional",
                        id
                );
                Timeline timeline = timeline(
                        resultSet.getString("status"),
                        instant(resultSet, "updated_at", "booking request", id)
                );
                plans.put(id, new BookingRequestPlan(
                        id,
                        clientDisplayName,
                        professionalDisplayName,
                        timeline.confirmedAt(),
                        timeline.rejectedAt(),
                        timeline.cancelledAt()
                ));
            }
        }

        return plans;
    }

    private static List<BookingItemPlan> loadBookingItemPlans(
            Connection connection,
            Map<Long, BookingRequestPlan> bookingRequests
    ) throws SQLException {
        String sql = "SELECT item.id, item.booking_request_id, slot.id AS slot_id, "
                + "slot.start_date_time, slot.end_date_time "
                + "FROM booking_request_items item "
                + "LEFT JOIN availability_slots slot ON slot.id = item.availability_slot_id "
                + "ORDER BY item.id";
        List<BookingItemPlan> plans = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                long id = requiredId(resultSet, "id", "booking item");
                long bookingRequestId = requiredId(resultSet, "booking_request_id", "booking item booking request");
                if (!bookingRequests.containsKey(bookingRequestId)) {
                    throw failure("booking item " + id + " references an unavailable booking request");
                }
                requirePresent(resultSet, "slot_id", "availability slot", id);

                Instant scheduledStart = instant(resultSet, "start_date_time", "availability slot", id);
                Instant scheduledEnd = instant(resultSet, "end_date_time", "availability slot", id);
                if (!scheduledEnd.isAfter(scheduledStart)) {
                    throw failure("booking item " + id + " has a non-positive legacy slot interval");
                }
                plans.add(new BookingItemPlan(id, bookingRequestId, scheduledStart, scheduledEnd));
            }
        }

        return plans;
    }

    private static void verifyEveryBookingHasAnItem(
            Map<Long, BookingRequestPlan> bookingRequests,
            List<BookingItemPlan> bookingItems
    ) {
        Map<Long, Integer> itemCounts = new LinkedHashMap<>();
        for (BookingItemPlan item : bookingItems) {
            itemCounts.merge(item.bookingRequestId(), 1, Integer::sum);
        }
        for (Long bookingRequestId : bookingRequests.keySet()) {
            if (!itemCounts.containsKey(bookingRequestId)) {
                throw failure("booking request " + bookingRequestId + " has no items to snapshot");
            }
        }
    }

    private static void addColumns(Connection connection) throws SQLException {
        execute(connection, "ALTER TABLE booking_requests ADD COLUMN client_display_name VARCHAR(201) NULL");
        execute(connection, "ALTER TABLE booking_requests ADD COLUMN professional_display_name VARCHAR(201) NULL");
        execute(connection, "ALTER TABLE booking_requests ADD COLUMN confirmed_at DATETIME(6) NULL");
        execute(connection, "ALTER TABLE booking_requests ADD COLUMN rejected_at DATETIME(6) NULL");
        execute(connection, "ALTER TABLE booking_requests ADD COLUMN cancelled_at DATETIME(6) NULL");
        execute(connection, "ALTER TABLE booking_request_items ADD COLUMN scheduled_start DATETIME(6) NULL");
        execute(connection, "ALTER TABLE booking_request_items ADD COLUMN scheduled_end DATETIME(6) NULL");
    }

    private static void applyBookingSnapshots(Connection connection, List<BookingRequestPlan> plans) throws SQLException {
        String sql = "UPDATE booking_requests SET client_display_name = ?, professional_display_name = ?, "
                + "confirmed_at = ?, rejected_at = ?, cancelled_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (BookingRequestPlan plan : plans) {
                statement.setString(1, plan.clientDisplayName());
                statement.setString(2, plan.professionalDisplayName());
                setUtcInstant(statement, 3, plan.confirmedAt());
                setUtcInstant(statement, 4, plan.rejectedAt());
                setUtcInstant(statement, 5, plan.cancelledAt());
                statement.setLong(6, plan.id());
                requireSingleUpdate(statement.executeUpdate(), "booking request " + plan.id());
            }
        }
    }

    private static void applyItemSnapshots(Connection connection, List<BookingItemPlan> plans) throws SQLException {
        String sql = "UPDATE booking_request_items SET scheduled_start = ?, scheduled_end = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (BookingItemPlan plan : plans) {
                setUtcInstant(statement, 1, plan.scheduledStart());
                setUtcInstant(statement, 2, plan.scheduledEnd());
                statement.setLong(3, plan.id());
                requireSingleUpdate(statement.executeUpdate(), "booking item " + plan.id());
            }
        }
    }

    private static void enforceRequiredSnapshots(Connection connection) throws SQLException {
        if (isH2(connection)) {
            execute(connection, "ALTER TABLE booking_requests ALTER COLUMN client_display_name SET NOT NULL");
            execute(connection, "ALTER TABLE booking_requests ALTER COLUMN professional_display_name SET NOT NULL");
            execute(connection, "ALTER TABLE booking_request_items ALTER COLUMN scheduled_start SET NOT NULL");
            execute(connection, "ALTER TABLE booking_request_items ALTER COLUMN scheduled_end SET NOT NULL");
            return;
        }

        execute(connection, "ALTER TABLE booking_requests MODIFY COLUMN client_display_name VARCHAR(201) NOT NULL");
        execute(connection, "ALTER TABLE booking_requests MODIFY COLUMN professional_display_name VARCHAR(201) NOT NULL");
        execute(connection, "ALTER TABLE booking_request_items MODIFY COLUMN scheduled_start DATETIME(6) NOT NULL");
        execute(connection, "ALTER TABLE booking_request_items MODIFY COLUMN scheduled_end DATETIME(6) NOT NULL");
    }

    private static void verifyPersistedSnapshots(Connection connection, MigrationPlan plan) throws SQLException {
        Map<Long, BookingRequestPlan> bookingsById = new LinkedHashMap<>();
        for (BookingRequestPlan booking : plan.bookingRequests()) {
            bookingsById.put(booking.id(), booking);
        }
        String bookingSql = "SELECT id, client_display_name, professional_display_name, confirmed_at, rejected_at, "
                + "cancelled_at FROM booking_requests ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(bookingSql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                long id = requiredId(resultSet, "id", "booking request");
                BookingRequestPlan expected = bookingsById.remove(id);
                if (expected == null
                        || !expected.clientDisplayName().equals(resultSet.getString("client_display_name"))
                        || !expected.professionalDisplayName().equals(resultSet.getString("professional_display_name"))
                        || !equalsInstant(expected.confirmedAt(), optionalInstant(resultSet, "confirmed_at", id))
                        || !equalsInstant(expected.rejectedAt(), optionalInstant(resultSet, "rejected_at", id))
                        || !equalsInstant(expected.cancelledAt(), optionalInstant(resultSet, "cancelled_at", id))) {
                    throw failure("booking request snapshot verification failed for id " + id);
                }
            }
        }
        if (!bookingsById.isEmpty()) {
            throw failure("booking request snapshot verification found missing rows");
        }

        Map<Long, BookingItemPlan> itemsById = new LinkedHashMap<>();
        for (BookingItemPlan item : plan.bookingItems()) {
            itemsById.put(item.id(), item);
        }
        String itemSql = "SELECT id, scheduled_start, scheduled_end FROM booking_request_items ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(itemSql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                long id = requiredId(resultSet, "id", "booking item");
                BookingItemPlan expected = itemsById.remove(id);
                if (expected == null
                        || !expected.scheduledStart().equals(instant(resultSet, "scheduled_start", "booking item", id))
                        || !expected.scheduledEnd().equals(instant(resultSet, "scheduled_end", "booking item", id))) {
                    throw failure("booking item snapshot verification failed for id " + id);
                }
            }
        }
        if (!itemsById.isEmpty()) {
            throw failure("booking item snapshot verification found missing rows");
        }
    }

    private static Timeline timeline(String rawStatus, Instant updatedAt) {
        if (rawStatus == null) {
            throw failure("booking request has a null status");
        }

        return switch (rawStatus.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> new Timeline(null, null, null);
            case "CONFIRMED" -> new Timeline(requiredUpdatedAt(updatedAt, rawStatus), null, null);
            case "REJECTED" -> new Timeline(null, requiredUpdatedAt(updatedAt, rawStatus), null);
            case "CANCELLED" -> new Timeline(null, null, requiredUpdatedAt(updatedAt, rawStatus));
            default -> throw failure("booking request has an unsupported status " + rawStatus);
        };
    }

    private static Instant requiredUpdatedAt(Instant updatedAt, String status) {
        if (updatedAt == null) {
            throw failure("booking request in status " + status + " has no updated_at for timeline backfill");
        }
        return updatedAt;
    }

    private static String displayName(String firstName, String lastName, String role, long bookingId) {
        String normalizedFirstName = firstName == null ? "" : firstName.trim();
        String normalizedLastName = lastName == null ? "" : lastName.trim();
        if (normalizedFirstName.isBlank() || normalizedLastName.isBlank()) {
            throw failure("booking request " + bookingId + " has an invalid " + role + " name");
        }
        String displayName = normalizedFirstName + " " + normalizedLastName;
        if (displayName.length() > DISPLAY_NAME_LENGTH) {
            throw failure("booking request " + bookingId + " has a " + role + " display name longer than "
                    + DISPLAY_NAME_LENGTH);
        }
        return displayName;
    }

    private static Instant instant(ResultSet resultSet, String column, String source, long id) throws SQLException {
        Instant value = optionalInstant(resultSet, column, id);
        if (value == null) {
            throw failure(source + " " + id + " has a null " + column);
        }
        return value;
    }

    private static Instant optionalInstant(ResultSet resultSet, String column, long id) throws SQLException {
        LocalDateTime value = resultSet.getObject(column, LocalDateTime.class);
        if (value == null) {
            return null;
        }
        if (value.getNano() % 1_000 != 0) {
            throw failure("row " + id + " has non-microsecond precision in " + column);
        }
        return value.toInstant(ZoneOffset.UTC);
    }

    private static boolean equalsInstant(Instant expected, Instant actual) {
        return expected == null ? actual == null : expected.equals(actual);
    }

    private static void requirePresent(ResultSet resultSet, String column, String source, long id) throws SQLException {
        if (resultSet.getObject(column) == null) {
            throw failure("booking request " + id + " has no " + source);
        }
    }

    private static long requiredId(ResultSet resultSet, String column, String source) throws SQLException {
        long value = resultSet.getLong(column);
        if (resultSet.wasNull()) {
            throw failure(source + " has a null " + column);
        }
        return value;
    }

    private static void setUtcInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setObject(index, LocalDateTime.ofInstant(value, ZoneOffset.UTC));
        }
    }

    private static void requireSingleUpdate(int updateCount, String source) {
        if (updateCount != 1) {
            throw failure("did not update exactly one " + source);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean isH2(Connection connection) throws SQLException {
        return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2");
    }

    private static FlywayException failure(String message) {
        return new FlywayException("V6 booking history migration precondition failed: " + message);
    }

    private record BookingRequestPlan(
            long id,
            String clientDisplayName,
            String professionalDisplayName,
            Instant confirmedAt,
            Instant rejectedAt,
            Instant cancelledAt
    ) {
    }

    private record BookingItemPlan(long id, long bookingRequestId, Instant scheduledStart, Instant scheduledEnd) {
    }

    private record Timeline(Instant confirmedAt, Instant rejectedAt, Instant cancelledAt) {
    }

    private record MigrationPlan(List<BookingRequestPlan> bookingRequests, List<BookingItemPlan> bookingItems) {
    }
}
