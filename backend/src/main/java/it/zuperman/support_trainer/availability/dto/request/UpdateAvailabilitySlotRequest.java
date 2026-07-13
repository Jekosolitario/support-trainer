package it.zuperman.support_trainer.availability.dto.request;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import it.zuperman.support_trainer.common.time.ValidBusinessDateTime;

public class UpdateAvailabilitySlotRequest {

    @ValidBusinessDateTime
    @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    private OffsetDateTime startDateTime;

    @ValidBusinessDateTime
    @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    private OffsetDateTime endDateTime;

    public UpdateAvailabilitySlotRequest() {
    }

    public UpdateAvailabilitySlotRequest(OffsetDateTime startDateTime, OffsetDateTime endDateTime) {
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
    }

    public OffsetDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(OffsetDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public OffsetDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(OffsetDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }
}
