package it.zuperman.support_trainer.professional.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

public interface ProfessionalProfileRepository extends JpaRepository<ProfessionalProfile, Long> {

    Optional<ProfessionalProfile> findByEmail(String email);
}
