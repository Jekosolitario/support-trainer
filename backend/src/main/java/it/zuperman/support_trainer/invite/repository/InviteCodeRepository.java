package it.zuperman.support_trainer.invite.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.zuperman.support_trainer.invite.entity.InviteCode;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    boolean existsByCode(String code);

    Optional<InviteCode> findByCode(String code);

    List<InviteCode> findAllByProfessional_IdOrderByCreatedAtDesc(Long professionalId);
}