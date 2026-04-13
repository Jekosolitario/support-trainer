package it.zuperman.support_trainer.professional.dto.response;

import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

public class ProfessionalSummaryResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
    private String specialization;
    private String operationalStatus;
    private Boolean active;

    public ProfessionalSummaryResponse() {
    }

    public ProfessionalSummaryResponse(
            Long id,
            String firstName,
            String lastName,
            String profileImageUrl,
            String specialization,
            String operationalStatus,
            Boolean active
    ) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.profileImageUrl = profileImageUrl;
        this.specialization = specialization;
        this.operationalStatus = operationalStatus;
        this.active = active;
    }

    public static ProfessionalSummaryResponse fromProfessional(ProfessionalProfile professional) {
        return new ProfessionalSummaryResponse(
                professional.getId(),
                professional.getFirstName(),
                professional.getLastName(),
                professional.getProfileImageUrl(),
                professional.getSpecialization() != null ? professional.getSpecialization().name() : null,
                professional.getOperationalStatus() != null ? professional.getOperationalStatus().name() : null,
                professional.getActive()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}