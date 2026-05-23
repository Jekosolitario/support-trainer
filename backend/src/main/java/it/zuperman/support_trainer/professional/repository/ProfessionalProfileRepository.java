package it.zuperman.support_trainer.professional.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import jakarta.persistence.LockModeType;

public interface ProfessionalProfileRepository extends JpaRepository<ProfessionalProfile, Long> {

    Optional<ProfessionalProfile> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT professional FROM ProfessionalProfile professional WHERE professional.id = :professionalId")
    Optional<ProfessionalProfile> findByIdForUpdate(@Param("professionalId") Long professionalId);
}
