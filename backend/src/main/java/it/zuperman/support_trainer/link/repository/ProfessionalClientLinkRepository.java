package it.zuperman.support_trainer.link.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

public interface ProfessionalClientLinkRepository extends JpaRepository<ProfessionalClientLink, Long> {

    long countByClient_IdAndActiveTrue(Long clientId);

    boolean existsByProfessional_IdAndClient_IdAndActiveTrue(Long professionalId, Long clientId);

    List<ProfessionalClientLink> findAllByProfessional_IdAndActiveTrue(Long professionalId);

    List<ProfessionalClientLink> findAllByClient_IdAndActiveTrue(Long clientId);

    @Query("""
            SELECT DISTINCT client
            FROM ProfessionalClientLink link
            JOIN link.client client
            WHERE link.professional.id = :professionalId
              AND client.id = :clientId
              AND link.active = true
              AND client.active = true
              AND client.accountStatus = :accountStatus
              AND client.emailVerified = true
            """)
    Optional<ClientProfile> findAccessibleClient(
            @Param("professionalId") Long professionalId,
            @Param("clientId") Long clientId,
            @Param("accountStatus") AccountStatus accountStatus
    );

    @Query("""
            SELECT DISTINCT professional
            FROM ProfessionalClientLink link
            JOIN link.professional professional
            WHERE link.client.id = :clientId
              AND professional.id = :professionalId
              AND link.active = true
              AND professional.active = true
              AND professional.accountStatus = :accountStatus
              AND professional.emailVerified = true
            """)
    Optional<ProfessionalProfile> findAccessibleProfessional(
            @Param("clientId") Long clientId,
            @Param("professionalId") Long professionalId,
            @Param("accountStatus") AccountStatus accountStatus
    );
}
