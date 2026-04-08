package it.zuperman.support_trainer.profile.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.enums.Role;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

public class MyProfileResponse {

    private Long id;
    private Role role;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
    private String operationalStatus;
    private Boolean active;

    // Professional fields
    private ProfessionalSpecialization specialization;
    private String phoneNumber;
    private String bio;
    private String workplaceName;
    private String city;
    private String instagramUrl;
    private String websiteUrl;

    // Client fields
    private LocalDate birthDate;
    private BigDecimal heightCm;
    private String primaryGoal;
    private Gender gender;
    private String medicalNotes;
    private String injuryNotes;
    private String notes;

    public MyProfileResponse() {
    }

    public static MyProfileResponse fromProfessional(ProfessionalProfile professional) {
        MyProfileResponse response = new MyProfileResponse();
        response.setId(professional.getId());
        response.setRole(professional.getRole());
        response.setFirstName(professional.getFirstName());
        response.setLastName(professional.getLastName());
        response.setProfileImageUrl(professional.getProfileImageUrl());
        response.setOperationalStatus(
                professional.getOperationalStatus() != null
                        ? professional.getOperationalStatus().name()
                        : null
        );
        response.setActive(professional.getActive());

        response.setSpecialization(professional.getSpecialization());
        response.setPhoneNumber(professional.getPhoneNumber());
        response.setBio(professional.getBio());
        response.setWorkplaceName(professional.getWorkplaceName());
        response.setCity(professional.getCity());
        response.setInstagramUrl(professional.getInstagramUrl());
        response.setWebsiteUrl(professional.getWebsiteUrl());

        return response;
    }

    public static MyProfileResponse fromClient(ClientProfile client) {
        MyProfileResponse response = new MyProfileResponse();
        response.setId(client.getId());
        response.setRole(client.getRole());
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
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

    public ProfessionalSpecialization getSpecialization() {
        return specialization;
    }

    public void setSpecialization(ProfessionalSpecialization specialization) {
        this.specialization = specialization;
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