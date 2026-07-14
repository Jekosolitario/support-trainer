package it.zuperman.support_trainer.client.dto.response;

import it.zuperman.support_trainer.client.entity.ClientProfile;

public class ClientDetailResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
    private String primaryGoal;

    public ClientDetailResponse() {
    }

    public static ClientDetailResponse fromClient(ClientProfile client) {
        ClientDetailResponse response = new ClientDetailResponse();
        response.setId(client.getId());
        response.setFirstName(client.getFirstName());
        response.setLastName(client.getLastName());
        response.setProfileImageUrl(client.getProfileImageUrl());
        response.setPrimaryGoal(client.getPrimaryGoal());

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
}
