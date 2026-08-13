package it.zuperman.support_trainer.client.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import jakarta.persistence.LockModeType;

public interface ClientProfileRepository extends JpaRepository<ClientProfile, Long> {

    Optional<ClientProfile> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT client FROM ClientProfile client WHERE client.id = :clientId")
    Optional<ClientProfile> findByIdForUpdate(@Param("clientId") Long clientId);
}
