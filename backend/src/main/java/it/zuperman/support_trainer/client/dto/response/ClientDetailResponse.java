package it.zuperman.support_trainer.client.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.enums.Gender;

public class ClientDetailResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
    private String operationalStatus;
    private Boolean active;

    private LocalDate birthDate;
    private BigDecimal heightCm;
    private String primaryGoal;
    private Gender gender;
    private String medicalNotes;
    private String injuryNotes;
    private String notes;

    public ClientDetailResponse() {
    }

    public static ClientDetailResponse fromClient(ClientProfile client) {
        ClientDetailResponse response = new ClientDetailResponse();
        response.setId(client.getId());
        response.setFirstName(client.getFirstName());
        response.setLastName(client.getLastName());
        response.setProfileImageUrl(client.getProfileImageUrl());
        response.setOperationalStatus(
                client.getOperationalStatus() != null
                        ? client.getOperationalStatus().name()
                        : null
        );
        response.setActive(client.getActive());

        response.setBirthDate(client.getBirthDate());
        response.setHeightCm(client.getHeightCm());
        response.setPrimaryGoal(client.getPrimaryGoal());
        response.setGender(client.getGender());
        response.setMedicalNotes(client.getMedicalNotes());
        response.setInjuryNotes(client.getInjuryNotes());
        response.setNotes(client.getNotes());

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

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public String getPrimaryGoal() {
        return primaryGoal;
    }

    public void setPrimaryGoal(String primaryGoal) {
        this.primaryGoal = primaryGoal;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getMedicalNotes() {
        return medicalNotes;
    }

    public void setMedicalNotes(String medicalNotes) {
        this.medicalNotes = medicalNotes;
    }

    public String getInjuryNotes() {
        return injuryNotes;
    }

    public void setInjuryNotes(String injuryNotes) {
        this.injuryNotes = injuryNotes;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}