package ro.ubm.monitorizare_medicala.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ro.ubm.monitorizare_medicala.model.VitalSign;

import java.util.List;

// Spring Data JPA generează implementarea și interogările din numele metodelor.
public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {
    List<VitalSign> findTop100ByPatientIdOrderByTimestampDescIdDesc(Long patientId);

    List<VitalSign> findTop100ByOrderByTimestampDescIdDesc();
}
