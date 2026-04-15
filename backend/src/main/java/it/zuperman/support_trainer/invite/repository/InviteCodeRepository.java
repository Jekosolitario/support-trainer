package it.zuperman.support_trainer.invite.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.zuperman.support_trainer.invite.entity.InviteCode;
import jakarta.persistence.LockModeType;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    boolean existsByCode(String code);

    Optional<InviteCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InviteCode i where i.code = :code")
    Optional<InviteCode> findByCodeForUpdate(@Param("code") String code);

    List<InviteCode> findAllByProfessional_IdOrderByCreatedAtDesc(Long professionalId);
}
