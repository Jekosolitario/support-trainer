package it.zuperman.support_trainer.common.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.zuperman.support_trainer.common.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
