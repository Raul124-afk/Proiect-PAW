package ro.ubm.monitorizare_medicala.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ro.ubm.monitorizare_medicala.model.MedicalNote;

public interface MedicalNoteRepository extends JpaRepository<MedicalNote, Long> {

    java.util.List<MedicalNote> findByPatientIdOrderByCreatedAtDesc(Long patientId);
}
