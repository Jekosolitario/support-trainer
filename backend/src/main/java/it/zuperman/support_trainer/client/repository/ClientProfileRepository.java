package it.zuperman.support_trainer.client.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.zuperman.support_trainer.client.entity.ClientProfile;

public interface ClientProfileRepository extends JpaRepository<ClientProfile, Long> {

    Optional<ClientProfile> findByEmail(String email);
}
