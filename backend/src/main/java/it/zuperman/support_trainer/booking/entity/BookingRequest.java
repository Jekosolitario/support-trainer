package it.zuperman.support_trainer.booking.entity;

import java.util.ArrayList;
import java.util.List;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.BaseEntity;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "booking_requests")
public class BookingRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientProfile client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private ProfessionalProfile professional;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private BookingRequestStatus status = BookingRequestStatus.PENDING;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @OneToMany(
            mappedBy = "bookingRequest",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<BookingRequestItem> items = new ArrayList<>();

    public BookingRequest(
            ClientProfile client,
            ProfessionalProfile professional,
            String note
    ) {
        this.client = client;
        this.professional = professional;
        this.note = note;
        this.status = BookingRequestStatus.PENDING;
        this.active = true;
    }
}