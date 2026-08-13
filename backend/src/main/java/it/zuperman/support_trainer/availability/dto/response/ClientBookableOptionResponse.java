package it.zuperman.support_trainer.availability.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record ClientBookableOptionResponse(
        OffsetDateTime startDateTime,
        List<Integer> allowedDurations
) {
}
