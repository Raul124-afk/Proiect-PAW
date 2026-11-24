package ro.ubm.monitorizare_medicala.controller;

import java.time.*;

public final class PortalInputs {
    private PortalInputs() {}

    public record PatientInput(
            String fullName,
            LocalDate birthDate,
            String sex,
            String email,
            String phone,
            Long doctorId,
            Long userId,
            Boolean demo) {}

    public record NoteInput(String body) {}

    public record AppointmentInput(
            Long patientId,
            LocalDateTime scheduledAt,
            Integer durationMinutes,
            String reason,
            String status) {}

    public record StatusInput(String status) {}

    public record RuleInput(
            Integer heartRateMin,
            Integer heartRateMax,
            Integer oxygenMin,
            Double temperatureMin,
            Double temperatureMax,
            Integer systolicMin,
            Integer systolicMax,
            Integer diastolicMin,
            Integer diastolicMax) {}

    public record UserInput(
            String username, String fullName, String password, String role, Boolean enabled) {}

    public record SimulationInput(Long patientId, Integer intervalSeconds, String scenario) {}
}
