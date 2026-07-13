package it.zuperman.support_trainer.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.common.exception.AppException;

@Component
public class BusinessDateTimeMapper {

    private final TimeProperties timeProperties;

    public BusinessDateTimeMapper(TimeProperties timeProperties) {
        this.timeProperties = timeProperties;
    }

    public void validateRequestDateTime(OffsetDateTime value) {
        if (value == null) {
            return;
        }

        if (value.getNano() != 0) {
            throw invalidRequest("Sono ammesse solo date e ore con precisione al secondo");
        }

        LocalDateTime localDateTime = value.toLocalDateTime();
        List<ZoneOffset> validOffsets = timeProperties.businessZone()
                .getRules()
                .getValidOffsets(localDateTime);

        if (validOffsets.isEmpty()) {
            throw invalidRequest("L'orario indicato non esiste nella zona business");
        }

        if (validOffsets.size() > 1) {
            throw invalidRequest("L'orario indicato è ambiguo nella zona business");
        }

        if (!validOffsets.getFirst().equals(value.getOffset())) {
            throw invalidRequest("L'offset indicato non è coerente con la zona business");
        }
    }

    public LocalDateTime toBusinessLocalDateTime(OffsetDateTime value) {
        if (value == null) {
            throw invalidRequest("La data e ora sono obbligatorie");
        }

        validateRequestDateTime(value);
        return value.toLocalDateTime();
    }

    public OffsetDateTime toBusinessOffsetDateTime(LocalDateTime value) {
        if (value == null || value.getNano() != 0) {
            throw invalidStoredDateTime();
        }

        List<ZoneOffset> validOffsets = timeProperties.businessZone()
                .getRules()
                .getValidOffsets(value);

        if (validOffsets.size() != 1) {
            throw invalidStoredDateTime();
        }

        return OffsetDateTime.of(value, validOffsets.getFirst());
    }

    private AppException invalidRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private AppException invalidStoredDateTime() {
        return new AppException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INVALID_STORED_SLOT_DATETIME",
                "L'orario dello slot non può essere rappresentato nella zona business"
        );
    }
}
