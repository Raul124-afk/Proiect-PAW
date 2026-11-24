package ro.ubm.monitorizare_medicala.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ro.ubm.monitorizare_medicala.model.AlertRule;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    java.util.Optional<AlertRule> findByPatientId(Long patientId);
}
