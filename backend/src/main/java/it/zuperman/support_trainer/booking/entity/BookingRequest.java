package it.zuperman.support_trainer.booking.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Setter(AccessLevel.NONE)
    @Column(name = "client_display_name", nullable = false, length = 201)
    private String clientDisplayName;

    @Setter(AccessLevel.NONE)
    @Column(name = "professional_display_name", nullable = false, length = 201)
    private String professionalDisplayName;

    @Setter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "confirmed_at", columnDefinition = "DATETIME(6)")
    private Instant confirmedAt;

    @Setter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "rejected_at", columnDefinition = "DATETIME(6)")
    private Instant rejectedAt;

    @Setter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "cancelled_at", columnDefinition = "DATETIME(6)")
    private Instant cancelledAt;

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
            String note,
            String clientDisplayName,
            String professionalDisplayName
    ) {
        this.client = client;
        this.professional = professional;
        this.note = note;
        this.clientDisplayName = clientDisplayName;
        this.professionalDisplayName = professionalDisplayName;
        this.status = BookingRequestStatus.PENDING;
        this.active = true;
    }

    public void confirm(Instant confirmedAt) {
        if (this.confirmedAt == null) {
            this.confirmedAt = confirmedAt;
        }
        this.status = BookingRequestStatus.CONFIRMED;
    }

    public void reject(Instant rejectedAt) {
        if (this.rejectedAt == null) {
            this.rejectedAt = rejectedAt;
        }
        this.status = BookingRequestStatus.REJECTED;
    }

    public void cancel(Instant cancelledAt) {
        if (this.cancelledAt == null) {
            this.cancelledAt = cancelledAt;
        }
        this.status = BookingRequestStatus.CANCELLED;
    }
}
