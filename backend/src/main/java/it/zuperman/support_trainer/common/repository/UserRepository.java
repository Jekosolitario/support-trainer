package it.zuperman.support_trainer.common.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.security.session.UserSecuritySnapshot;
import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    @Query("""
            select new it.zuperman.support_trainer.security.session.UserSecuritySnapshot(
                user.id,
                user.role,
                user.accountStatus,
                user.emailVerified,
                user.sessionVersion
            )
            from User user
            where user.id = :userId
            """)
    Optional<UserSecuritySnapshot> findSecuritySnapshotById(@Param("userId") Long userId);
}
