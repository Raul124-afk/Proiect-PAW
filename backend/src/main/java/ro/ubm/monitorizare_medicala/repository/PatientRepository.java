package ro.ubm.monitorizare_medicala.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ro.ubm.monitorizare_medicala.model.Patient;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    java.util.Optional<Patient> findByUserId(Long userId);

    java.util.List<Patient> findByDoctorId(Long doctorId);

    boolean existsByDoctorId(Long doctorId);
}
