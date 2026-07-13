package it.zuperman.support_trainer.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class ApplicationTimeProvider {

    private final Clock clock;
    private final ZoneId businessZone;

    public ApplicationTimeProvider(Clock clock, TimeProperties timeProperties) {
        this.clock = clock;
        this.businessZone = timeProperties.businessZone();
    }

    public Instant nowInstant() {
        return clock.instant();
    }

    public LocalDateTime nowBusinessDateTime() {
        return toBusinessDateTime(nowInstant());
    }

    public LocalDate todayBusiness() {
        return LocalDate.ofInstant(nowInstant(), businessZone);
    }

    public LocalDateTime toBusinessDateTime(Instant instant) {
        return LocalDateTime.ofInstant(Objects.requireNonNull(instant, "instant must not be null"), businessZone);
    }

    public ZoneId businessZone() {
        return businessZone;
    }
}
