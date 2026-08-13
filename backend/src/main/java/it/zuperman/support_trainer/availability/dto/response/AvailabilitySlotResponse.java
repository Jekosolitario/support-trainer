package it.zuperman.support_trainer.availability.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.service.AvailabilityWindowPolicy;
import it.zuperman.support_trainer.common.time.BusinessDateTimeMapper;

public class AvailabilitySlotResponse {

    private Long id;
    private OffsetDateTime startDateTime;
    private OffsetDateTime endDateTime;
    private String status;
    private Boolean active;
    private Long weeklyRuleId;
    private String locationLabel;
    private Integer capacity;
    private Long maximumOccupancy;
    private Long minimumRemainingCapacity;
    private List<Integer> allowedDurations;
    private Integer startIntervalMinutes;
    private Boolean blocked;
    private Boolean bookable;

    public AvailabilitySlotResponse() {
    }

    public AvailabilitySlotResponse(
            Long id,
            OffsetDateTime startDateTime,
            OffsetDateTime endDateTime,
            String status,
            Boolean active
    ) {
        this.id = id;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.status = status;
        this.active = active;
    }

    public static AvailabilitySlotResponse fromEntity(
            AvailabilitySlot slot,
            BusinessDateTimeMapper businessDateTimeMapper
    ) {
        return new AvailabilitySlotResponse(
                slot.getId(),
                businessDateTimeMapper.toBusinessOffsetDateTime(slot.getStartDateTime()),
                businessDateTimeMapper.toBusinessOffsetDateTime(slot.getEndDateTime()),
                slot.getStatus() != null ? slot.getStatus().name() : null,
                slot.getActive()
        );
    }

    public static AvailabilitySlotResponse fromEntity(
            AvailabilitySlot slot,
            long maximumOccupancy,
            boolean bookable,
            BusinessDateTimeMapper businessDateTimeMapper
    ) {
        AvailabilitySlotResponse response = fromEntity(slot, businessDateTimeMapper);
        response.weeklyRuleId = slot.getWeeklyRule() == null ? null : slot.getWeeklyRule().getId();
        response.locationLabel = slot.getLocationLabel();
        response.capacity = slot.getCapacity();
        response.maximumOccupancy = maximumOccupancy;
        response.minimumRemainingCapacity = Math.max(0L, slot.getCapacity() - maximumOccupancy);
        response.allowedDurations = AvailabilityWindowPolicy.allowedDurations(slot);
        response.startIntervalMinutes = AvailabilityWindowPolicy.START_INTERVAL_MINUTES;
        response.blocked = slot.getBlocked();
        response.bookable = bookable;
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Long getWeeklyRuleId() {
        return weeklyRuleId;
    }

    public void setWeeklyRuleId(Long weeklyRuleId) {
        this.weeklyRuleId = weeklyRuleId;
    }

    public String getLocationLabel() {
        return locationLabel;
    }

    public void setLocationLabel(String locationLabel) {
        this.locationLabel = locationLabel;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Long getMaximumOccupancy() {
        return maximumOccupancy;
    }

    public void setMaximumOccupancy(Long maximumOccupancy) {
        this.maximumOccupancy = maximumOccupancy;
    }

    public Long getMinimumRemainingCapacity() {
        return minimumRemainingCapacity;
    }

    public void setMinimumRemainingCapacity(Long minimumRemainingCapacity) {
        this.minimumRemainingCapacity = minimumRemainingCapacity;
    }

    public List<Integer> getAllowedDurations() {
        return allowedDurations;
    }

    public void setAllowedDurations(List<Integer> allowedDurations) {
        this.allowedDurations = allowedDurations;
    }

    public Integer getStartIntervalMinutes() {
        return startIntervalMinutes;
    }

    public void setStartIntervalMinutes(Integer startIntervalMinutes) {
        this.startIntervalMinutes = startIntervalMinutes;
    }

    public Boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(Boolean blocked) {
        this.blocked = blocked;
    }

    public Boolean getBookable() {
        return bookable;
    }

    public void setBookable(Boolean bookable) {
        this.bookable = bookable;
    }
}
