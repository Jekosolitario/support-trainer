package it.zuperman.support_trainer.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public class BookingParticipantResponse {

    private final Long id;
    private final String displayName;
    private final String profileImageUrl;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String specialization;

    public BookingParticipantResponse(
            Long id,
            String displayName,
            String profileImageUrl,
            String specialization
    ) {
        this.id = id;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
        this.specialization = specialization;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getSpecialization() {
        return specialization;
    }
}
