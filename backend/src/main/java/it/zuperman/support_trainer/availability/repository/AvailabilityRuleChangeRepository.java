package it.zuperman.support_trainer.availability.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.zuperman.support_trainer.availability.entity.AvailabilityRuleChange;

public interface AvailabilityRuleChangeRepository extends JpaRepository<AvailabilityRuleChange, Long> {
}
