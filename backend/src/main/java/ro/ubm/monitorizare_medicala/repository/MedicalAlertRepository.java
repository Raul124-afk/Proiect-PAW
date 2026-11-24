package ro.ubm.monitorizare_medicala.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ro.ubm.monitorizare_medicala.model.MedicalAlert;

public interface MedicalAlertRepository extends JpaRepository<MedicalAlert, Long> {

    java.util.List<MedicalAlert> findByPatientIdInOrderByCreatedAtDesc(
            java.util.Collection<Long> patientIds);
}
