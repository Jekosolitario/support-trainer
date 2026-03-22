package it.zuperman.support_trainer.link.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;

public interface ProfessionalClientLinkRepository extends JpaRepository<ProfessionalClientLink, Long> {

    long countByClient_IdAndActiveTrue(Long clientId);

    boolean existsByProfessional_IdAndClient_IdAndActiveTrue(Long professionalId, Long clientId);
}