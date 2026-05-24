package it.zuperman.support_trainer.profile.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.zuperman.support_trainer.common.enums.Gender;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateMyProfileRequest {

    @Size(max = 100, message = "Il nome non può superare 100 caratteri")
    private String firstName;

    @Size(max = 100, message = "Il cognome non può superare 100 caratteri")
    private String lastName;

    // Professional fields
    @Size(max = 30, message = "Il numero di telefono non può superare 30 caratteri")
    private String phoneNumber;

    @Size(max = 5000, message = "La bio non può superare 5000 caratteri")
    private String bio;

    @Size(max = 150, message = "Il nome del luogo di lavoro non può superare 150 caratteri")
    private String workplaceName;

    @Size(max = 100, message = "La città non può superare 100 caratteri")
    private String city;

    @Size(max = 255, message = "L'URL Instagram non può superare 255 caratteri")
    @Pattern(
            regexp = "^(\\s*|https?://.+)$",
            message = "L'URL Instagram deve iniziare con http:// o https://"
    )
    private String instagramUrl;

    @Size(max = 255, message = "L'URL del sito web non può superare 255 caratteri")
    @Pattern(
            regexp = "^(\\s*|https?://.+)$",
            message = "L'URL del sito web deve iniziare con http:// o https://"
    )
    private String websiteUrl;

    // Client fields
    @Past(message = "La data di nascita deve essere nel passato")
    private LocalDate birthDate;

    @DecimalMin(value = "0.01", message = "L'altezza deve essere maggiore di 0")
    @Digits(integer = 3, fraction = 2, message = "L'altezza deve avere massimo 3 cifre intere e 2 decimali")
    private BigDecimal heightCm;

    @Size(max = 255, message = "L'obiettivo principale non può superare 255 caratteri")
    private String primaryGoal;

    private Gender gender;

    @Size(max = 5000, message = "Le note mediche non possono superare 5000 caratteri")
    private String medicalNotes;

    @Size(max = 5000, message = "Le note sugli infortuni non possono superare 5000 caratteri")
    private String injuryNotes;

    @Size(max = 5000, message = "Le note non possono superare 5000 caratteri")
    private String notes;

    public UpdateMyProfileRequest() {
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
