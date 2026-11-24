package ro.ubm.monitorizare_medicala.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ro.ubm.monitorizare_medicala.model.*;
import ro.ubm.monitorizare_medicala.repository.*;
import ro.ubm.monitorizare_medicala.service.AlertEvaluator;

import java.time.*;
import java.util.*;

@Component
@Order(50)
public class PortalBootstrap implements CommandLineRunner {
    private final UserRepository users;
    private final PatientRepository patients;
    private final VitalSignRepository vitals;
    private final AppointmentRepository appointments;
    private final MedicalNoteRepository notes;
    private final AlertEvaluator evaluator;
    private final PasswordEncoder encoder;
    private final Environment env;

    public PortalBootstrap(
            UserRepository users,
            PatientRepository patients,
            VitalSignRepository vitals,
            AppointmentRepository appointments,
            MedicalNoteRepository notes,
            AlertEvaluator evaluator,
            PasswordEncoder encoder,
            Environment env) {
        this.users = users;
        this.patients = patients;
        this.vitals = vitals;
        this.appointments = appointments;
        this.notes = notes;
        this.evaluator = evaluator;
        this.encoder = encoder;
        this.env = env;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String adminPassword = env.getProperty("app.admin.password", "");
        if (!adminPassword.isBlank())
            account(
                    env.getProperty("app.admin.username", "admin"),
                    adminPassword,
                    "Administrator",
                    "ROLE_ADMIN");
        // Păstrează măsurătorile aplicației inițiale și le atașează unor fișe explicite.
        if (patients.count() == 0 && vitals.count() > 0) {
            Long doctor =
                    users.findAll().stream()
                            .filter(u -> "ROLE_MEDIC".equals(u.getRole()))
                            .map(User::getId)
                            .findFirst()
                            .orElse(null);
            var old = vitals.findAll();
            var mapping = new HashMap<Long, Long>();
            for (var v : old)
                if (!mapping.containsKey(v.getPatientId())) {
                    var p = new Patient();
                    p.setFullName("Pacient importat #" + v.getPatientId());
                    p.setDoctorId(doctor);
                    patients.saveAndFlush(p);
                    mapping.put(v.getPatientId(), p.getId());
                }
            // Capturăm toate rândurile înainte de modificare, pentru a evita coliziuni între
            // ID-uri.
            for (var v : old) v.setPatientId(mapping.get(v.getPatientId()));
            vitals.saveAll(old);
        }
        if (Arrays.asList(env.getActiveProfiles()).contains("demo")) seedDemo();
        for (var u : users.findAll())
            if ("ROLE_PATIENT".equals(u.getRole()) && patients.findByUserId(u.getId()).isEmpty()) {
                var p = new Patient();
                p.setFullName(u.getFullName());
                p.setUserId(u.getId());
                patients.save(p);
            }
    }

    private User account(String username, String password, String name, String role) {
        if (password.length() < 12
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException(
                    "Parola inițială trebuie să aibă 12 caractere și maximum 72 de octeți.");
        return users.findByUsername(username)
                .orElseGet(
                        () ->
                                users.saveAndFlush(
                                        new User(
                                                null,
                                                username,
                                                encoder.encode(password),
                                                name,
                                                role)));
    }

    private void seedDemo() {
        account("admin_demo", "DemoAdmin2026!", "Administrator demo", "ROLE_ADMIN");
        var medic = account("medic", "DemoMedical2026!", "Dr. Andrei Popescu", "ROLE_MEDIC");
        var medic2 = account("medic2_demo", "DemoMedical2026!", "Dr. Elena Marin", "ROLE_MEDIC");
        var patientUser =
                account("pacient_demo", "DemoPacient2026!", "Ana Ionescu", "ROLE_PATIENT");
        if (patients.findAll().stream().anyMatch(Patient::isDemo)) return;
        String[] names = {"Ana Ionescu", "Mihai Dumitrescu", "Ioana Georgescu"};
        for (int i = 0; i < 3; i++) {
            var p = new Patient();
            p.setFullName(names[i]);
            p.setBirthDate(LocalDate.of(1986 + i * 6, 3 + i, 12));
            p.setSex(i == 1 ? "Masculin" : "Feminin");
            p.setEmail("pacient" + (i + 1) + "@example.test");
            p.setPhone("0700 000 00" + i);
            p.setDemo(true);
            p.setDoctorId(i == 2 ? medic2.getId() : medic.getId());
            if (i == 0) p.setUserId(patientUser.getId());
            patients.saveAndFlush(p);
            for (int day = 6; day >= 0; day--)
                for (int k = 0; k < 3; k++) {
                    boolean spike = day == 0 && k == 2 && i == 0;
                    var v =
                            new VitalSign(
                                    null,
                                    p.getId(),
                                    spike ? 128 : 68 + i * 5 + k * 3,
                                    spike ? 91 : 97 + k % 3,
                                    spike ? "158/98" : "120/80",
                                    spike ? 38.4 : 36.4 + k * .2,
                                    LocalDate.now().minusDays(day).atTime(8 + k * 3, 10));
                    if (v.getTimestamp().isAfter(LocalDateTime.now()))
                        v.setTimestamp(LocalDateTime.now().minusMinutes(10 - k));
                    vitals.saveAndFlush(v);
                    evaluator.evaluate(v);
                }
            var note = new MedicalNote();
            note.setPatientId(p.getId());
            note.setAuthorId(p.getDoctorId());
            note.setBody(
                    "Fișă demonstrativă. Urmărim evoluția măsurătorilor și actualizarea graficelor."
                        + " Toate datele acestei fișe sunt fictive.");
            notes.save(note);
            var a = new Appointment();
            a.setPatientId(p.getId());
            a.setDoctorId(p.getDoctorId());
            a.setScheduledAt(LocalDate.now().plusDays(i + 1).atTime(10 + i, 0));
            a.setReason("Consultație de monitorizare — demonstrație");
            appointments.save(a);
        }
    }
}
