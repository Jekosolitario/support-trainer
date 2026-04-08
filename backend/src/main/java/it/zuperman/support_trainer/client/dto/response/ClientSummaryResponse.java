package it.zuperman.support_trainer.client.dto.response;

import it.zuperman.support_trainer.client.entity.ClientProfile;

public class ClientSummaryResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
    private String operationalStatus;
    private String primaryGoal;
    private Boolean active;

    public ClientSummaryResponse() {
    }

    public ClientSummaryResponse(
            Long id,
            String firstName,
            String lastName,
            String profileImageUrl,
            String operationalStatus,
            String primaryGoal,
            Boolean active
    ) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.profileImageUrl = profileImageUrl;
        this.operationalStatus = operationalStatus;
        this.primaryGoal = primaryGoal;
        this.active = active;
    }

    public static ClientSummaryResponse fromClient(ClientProfile client) {
        return new ClientSummaryResponse(
                client.getId(),
                client.getFirstName(),
                client.getLastName(),
                client.getProfileImageUrl(),
                client.getOperationalStatus() != null ? client.getOperationalStatus().name() : null,
                client.getPrimaryGoal(),
                client.getActive()
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

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public String getPrimaryGoal() {
        return primaryGoal;
    }

    public void setPrimaryGoal(String primaryGoal) {
        this.primaryGoal = primaryGoal;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}