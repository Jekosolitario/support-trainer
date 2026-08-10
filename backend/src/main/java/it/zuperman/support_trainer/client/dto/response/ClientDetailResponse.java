package it.zuperman.support_trainer.client.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.enums.ClientOperationalStatus;
import it.zuperman.support_trainer.common.enums.Gender;

public class ClientDetailResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
    private String primaryGoal;
    private ClientOperationalStatus operationalStatus;
    private LocalDate birthDate;
    private BigDecimal heightCm;
    private Gender gender;

    public ClientDetailResponse() {
    }

    public static ClientDetailResponse fromClient(ClientProfile client) {
        ClientDetailResponse response = new ClientDetailResponse();
        response.setId(client.getId());
        response.setFirstName(client.getFirstName());
        response.setLastName(client.getLastName());
        response.setProfileImageUrl(client.getProfileImageUrl());
        response.setPrimaryGoal(client.getPrimaryGoal());
        response.setOperationalStatus(client.getOperationalStatus());
        response.setBirthDate(client.getBirthDate());
        response.setHeightCm(client.getHeightCm());
        response.setGender(client.getGender());

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

    public String getPrimaryGoal() {
        return primaryGoal;
    }

    public void setPrimaryGoal(String primaryGoal) {
        this.primaryGoal = primaryGoal;
    }

    public ClientOperationalStatus getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(ClientOperationalStatus operationalStatus) {
        this.operationalStatus = operationalStatus;
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

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }
}
