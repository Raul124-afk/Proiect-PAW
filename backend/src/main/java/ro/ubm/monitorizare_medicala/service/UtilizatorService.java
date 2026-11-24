package ro.ubm.monitorizare_medicala.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.ubm.monitorizare_medicala.model.User;
import ro.ubm.monitorizare_medicala.repository.UserRepository;

import java.nio.charset.StandardCharsets;

@Service
@Transactional(readOnly = true)
public class UtilizatorService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final ro.ubm.monitorizare_medicala.repository.PatientRepository patients;

    public UtilizatorService(
            UserRepository users,
            PasswordEncoder encoder,
            ro.ubm.monitorizare_medicala.repository.PatientRepository patients) {
        this.users = users;
        this.encoder = encoder;
        this.patients = patients;
    }

    @Transactional
    public void inregistreaza(String nume, String username, String parola) {
        if (users.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Numele de utilizator este deja folosit.");
        }
        // BCrypt acceptă maximum 72 de octeți, inclusiv pentru caractere cu diacritice.
        if (parola.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Parola este prea lungă în format UTF-8 (maximum 72 de octeți).");
        }
        // Rol fix pe server: formularul nu poate crea medici.
        var saved =
                users.saveAndFlush(
                        new User(
                                null,
                                username,
                                encoder.encode(parola),
                                nume.trim(),
                                "ROLE_PATIENT"));
        var patient = new ro.ubm.monitorizare_medicala.model.Patient();
        patient.setFullName(nume.trim());
        patient.setUserId(saved.getId());
        patients.save(patient);
    }

    public User gaseste(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Utilizator inexistent."));
    }
}
