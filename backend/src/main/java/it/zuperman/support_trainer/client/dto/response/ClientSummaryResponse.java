package it.zuperman.support_trainer.client.dto.response;

import it.zuperman.support_trainer.client.entity.ClientProfile;

public class ClientSummaryResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String profileImageUrl;

    public ClientSummaryResponse() {
    }

    public ClientSummaryResponse(
            Long id,
            String firstName,
            String lastName,
            String profileImageUrl
    ) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.profileImageUrl = profileImageUrl;
    }

    public static ClientSummaryResponse fromClient(ClientProfile client) {
        return new ClientSummaryResponse(
                client.getId(),
                client.getFirstName(),
                client.getLastName(),
                client.getProfileImageUrl()
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
}
