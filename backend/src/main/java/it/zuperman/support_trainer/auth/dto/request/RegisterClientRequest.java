package it.zuperman.support_trainer.auth.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.zuperman.support_trainer.common.enums.Gender;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterClientRequest {

    @NotBlank(message = "Il nome è obbligatorio")
    @Size(max = 50, message = "Il nome non può superare 50 caratteri")
    private String firstName;

    @NotBlank(message = "Il cognome è obbligatorio")
    @Size(max = 50, message = "Il cognome non può superare 50 caratteri")
    private String lastName;

    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Formato email non valido")
    @Size(max = 100, message = "L'email non può superare 100 caratteri")
    private String email;

    @NotBlank(message = "La password è obbligatoria")
    @Size(min = 8, max = 100, message = "La password deve essere tra 8 e 100 caratteri")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "La password deve contenere almeno una maiuscola, un numero e un carattere speciale"
    )
    private String password;

    @NotBlank(message = "Il codice invito è obbligatorio")
    @Size(max = 100, message = "Il codice invito non può superare 100 caratteri")
    private String inviteCode;

    @NotNull(message = "La data di nascita è obbligatoria")
    @Past(message = "La data di nascita deve essere nel passato")
    private LocalDate birthDate;

    @NotNull(message = "L'altezza è obbligatoria")
    @DecimalMin(value = "0.01", message = "L'altezza deve essere maggiore di 0")
    @Digits(integer = 3, fraction = 2, message = "L'altezza deve avere massimo 3 cifre intere e 2 decimali")
    private BigDecimal heightCm;

    @NotBlank(message = "L'obiettivo principale è obbligatorio")
    @Size(max = 255, message = "L'obiettivo principale non può superare 255 caratteri")
    private String primaryGoal;

    @NotNull(message = "Il genere è obbligatorio")
    private Gender gender;

    private String medicalNotes;
    private String injuryNotes;
    private String notes;

    public RegisterClientRequest() {
    }

    public RegisterClientRequest(
            String firstName,
            String lastName,
            String email,
            String password,
            String inviteCode,
            LocalDate birthDate,
            BigDecimal heightCm,
            String primaryGoal,
            Gender gender,
            String medicalNotes,
            String injuryNotes,
            String notes
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
        this.inviteCode = inviteCode;
        this.birthDate = birthDate;
        this.heightCm = heightCm;
        this.primaryGoal = primaryGoal;
        this.gender = gender;
        this.medicalNotes = medicalNotes;
        this.injuryNotes = injuryNotes;
        this.notes = notes;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
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
