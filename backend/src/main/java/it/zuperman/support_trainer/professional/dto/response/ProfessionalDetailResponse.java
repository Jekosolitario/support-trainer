package it.zuperman.support_trainer.professional.dto.response;

import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

public class ProfessionalDetailResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
    private String specialization;
    private String operationalStatus;
    private Boolean active;

    private String phoneNumber;
    private String bio;
    private String workplaceName;
    private String city;
    private String instagramUrl;
    private String websiteUrl;

    public ProfessionalDetailResponse() {
    }

    public static ProfessionalDetailResponse fromProfessional(ProfessionalProfile professional) {
        ProfessionalDetailResponse response = new ProfessionalDetailResponse();
        response.setId(professional.getId());
        response.setFirstName(professional.getFirstName());
        response.setLastName(professional.getLastName());
        response.setProfileImageUrl(professional.getProfileImageUrl());
        response.setSpecialization(
                professional.getSpecialization() != null
                        ? professional.getSpecialization().name()
                        : null
        );
        response.setOperationalStatus(
                professional.getOperationalStatus() != null
                        ? professional.getOperationalStatus().name()
                        : null
        );
        response.setActive(professional.getActive());

        response.setPhoneNumber(professional.getPhoneNumber());
        response.setBio(professional.getBio());
        response.setWorkplaceName(professional.getWorkplaceName());
        response.setCity(professional.getCity());
        response.setInstagramUrl(professional.getInstagramUrl());
        response.setWebsiteUrl(professional.getWebsiteUrl());

        return response;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getWorkplaceName() {
        return workplaceName;
    }

    public void setWorkplaceName(String workplaceName) {
        this.workplaceName = workplaceName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public void setInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }
}