package ro.ubm.monitorizare_medicala.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ro.ubm.monitorizare_medicala.model.Appointment;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    java.util.List<Appointment> findByDoctorIdAndStatus(Long doctorId, String status);
}
