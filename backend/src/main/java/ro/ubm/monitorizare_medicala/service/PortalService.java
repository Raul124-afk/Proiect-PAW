package ro.ubm.monitorizare_medicala.service;

import org.springframework.core.env.Environment;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ro.ubm.monitorizare_medicala.controller.PortalInputs.*;
import ro.ubm.monitorizare_medicala.model.*;
import ro.ubm.monitorizare_medicala.repository.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PortalService {
    private final AccessService access;
    private final PatientRepository patients;
    private final UserRepository users;
    private final VitalSignRepository vitals;
    private final MedicalNoteRepository notes;
    private final AppointmentRepository appointments;
    private final MedicalAlertRepository alerts;
    private final AlertRuleRepository rules;
    private final AlertEvaluator evaluator;
    private final PasswordEncoder encoder;
    private final Environment env;

    public PortalService(
            AccessService access,
            PatientRepository patients,
            UserRepository users,
            VitalSignRepository vitals,
            MedicalNoteRepository notes,
            AppointmentRepository appointments,
            MedicalAlertRepository alerts,
            AlertRuleRepository rules,
            AlertEvaluator evaluator,
            PasswordEncoder encoder,
            Environment env) {
        this.access = access;
        this.patients = patients;
        this.users = users;
        this.vitals = vitals;
        this.notes = notes;
        this.appointments = appointments;
        this.alerts = alerts;
        this.rules = rules;
        this.evaluator = evaluator;
        this.encoder = encoder;
        this.env = env;
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    public Map<String, Object> me() {
        var u = access.current();
        return map(
                "id",
                u.getId(),
                "username",
                u.getUsername(),
                "fullName",
                u.getFullName(),
                "role",
                u.getRole(),
                "demoMode",
                Arrays.asList(env.getActiveProfiles()).contains("demo"),
                "patientId",
                patients.findByUserId(u.getId()).map(Patient::getId).orElse(null));
    }

    private String name(Long id) {
        return id == null
                ? "Nerepartizat"
                : users.findById(id)
                        .map(u -> u.getFullName() == null ? u.getUsername() : u.getFullName())
                        .orElse("Cont indisponibil");
    }

    public Map<String, Object> patientView(Patient p) {
        return map(
                "id",
                p.getId(),
                "fullName",
                p.getFullName(),
                "birthDate",
                p.getBirthDate(),
                "age",
                p.getBirthDate() == null
                        ? null
                        : Period.between(p.getBirthDate(), LocalDate.now()).getYears(),
                "sex",
                p.getSex(),
                "email",
                p.getEmail(),
                "phone",
                p.getPhone(),
                "doctorId",
                p.getDoctorId(),
                "doctorName",
                name(p.getDoctorId()),
                "userId",
                p.getUserId(),
                "username",
                p.getUserId() == null
                        ? null
                        : users.findById(p.getUserId()).map(User::getUsername).orElse(null),
                "demo",
                p.isDemo(),
                "archived",
                p.isArchived(),
                "createdAt",
                p.getCreatedAt());
    }

    public List<Map<String, Object>> patientList(String q, boolean archived) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return access.visiblePatients().stream()
                .filter(p -> p.isArchived() == archived)
                .filter(
                        p ->
                                (p.getFullName()
                                                + " "
                                                + p.getId()
                                                + " "
                                                + Objects.toString(p.getEmail(), ""))
                                        .toLowerCase(Locale.ROOT)
                                        .contains(query))
                .sorted(Comparator.comparing(Patient::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(this::patientView)
                .toList();
    }

    public Map<String, Object> detail(Long id) {
        var p = access.patient(id);
        var n =
                notes.findByPatientIdOrderByCreatedAtDesc(id).stream()
                        .map(
                                x ->
                                        map(
                                                "id",
                                                x.getId(),
                                                "body",
                                                x.getBody(),
                                                "author",
                                                name(x.getAuthorId()),
                                                "createdAt",
                                                x.getCreatedAt()))
                        .toList();
        return map(
                "patient",
                patientView(p),
                "notes",
                n,
                "rules",
                evaluator.rule(id),
                "vitals",
                vitals.findTop100ByPatientIdOrderByTimestampDescIdDesc(id));
    }

    private static String text(String value, int min, int max, String label) {
        String s = value == null ? "" : value.trim();
        if (s.length() < min || s.length() > max)
            throw new IllegalArgumentException(
                    label + ": între " + min + " și " + max + " de caractere.");
        return s;
    }

    private Long doctor(Long id) {
        if (id == null) return null;
        var d =
                users.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Medicul nu există."));
        if (!"ROLE_MEDIC".equals(d.getRole()) || !d.isEnabled())
            throw new IllegalArgumentException("Alege un medic activ.");
        return id;
    }

    @Transactional
    public Map<String, Object> savePatient(Long id, PatientInput in) {
        access.requireStaff();
        var actor = access.current();
        Patient p = id == null ? new Patient() : access.patient(id);
        p.setFullName(text(in.fullName(), 2, 100, "Numele"));
        if (in.birthDate() != null
                && (in.birthDate().isAfter(LocalDate.now())
                        || in.birthDate().isBefore(LocalDate.now().minusYears(130))))
            throw new IllegalArgumentException("Data nașterii nu este validă.");
        p.setBirthDate(in.birthDate());
        p.setSex(text(in.sex(), 0, 20, "Sexul"));
        String email = text(in.email(), 0, 120, "Emailul");
        if (!email.isEmpty() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("Adresa de email nu este validă.");
        p.setEmail(email);
        String phone = text(in.phone(), 0, 30, "Telefonul");
        if (!phone.isEmpty() && !phone.matches("[+0-9() .-]{5,30}"))
            throw new IllegalArgumentException("Numărul de telefon nu este valid.");
        p.setPhone(phone);
        if (access.admin(actor)) {
            Long doctorId = doctor(in.doctorId());
            if (id != null
                    && !Objects.equals(doctorId, p.getDoctorId())
                    && appointments.findAll().stream()
                            .anyMatch(
                                    a ->
                                            id.equals(a.getPatientId())
                                                    && "PROGRAMATA".equals(a.getStatus())
                                                    && a.getScheduledAt()
                                                            .isAfter(LocalDateTime.now())))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Anulează sau finalizează programările viitoare înainte de schimbarea"
                            + " medicului.");
            p.setDoctorId(doctorId);
            if (in.userId() != null) {
                var u =
                        users.findById(in.userId())
                                .orElseThrow(
                                        () -> new IllegalArgumentException("Cont inexistent."));
                if (!"ROLE_PATIENT".equals(u.getRole()) || !u.isEnabled())
                    throw new IllegalArgumentException(
                            "Fișa se poate asocia doar unui cont de pacient activ.");
                var linked = patients.findByUserId(u.getId());
                if (linked.isPresent() && !linked.get().getId().equals(id))
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Contul are deja o fișă. Editează fișa existentă.");
            }
            p.setUserId(in.userId());
        } else {
            if (in.doctorId() != null && !actor.getId().equals(in.doctorId()))
                throw new AccessDeniedException("Doar administratorul poate repartiza alt medic.");
            if (!Objects.equals(in.userId(), p.getUserId()))
                throw new AccessDeniedException("Doar administratorul poate asocia un cont.");
            p.setDoctorId(actor.getId());
        }
        if (id == null) p.setDemo(Boolean.TRUE.equals(in.demo()));
        else if (in.demo() != null && in.demo() != p.isDemo())
            throw new IllegalArgumentException(
                    "Tipul demonstrativ al unei fișe existente nu se poate schimba.");
        return patientView(patients.saveAndFlush(p));
    }

    @Transactional
    public Map<String, Object> archivePatient(Long id, boolean archived) {
        access.requireStaff();
        var p = access.patient(id);
        if (archived
                && appointments.findAll().stream()
                        .anyMatch(
                                a ->
                                        id.equals(a.getPatientId())
                                                && "PROGRAMATA".equals(a.getStatus())
                                                && a.getScheduledAt().isAfter(LocalDateTime.now())))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pacientul are programări viitoare. Anulează-le înainte de arhivare.");
        p.setArchived(archived);
        return patientView(patients.save(p));
    }

    @Transactional
    public Map<String, Object> addNote(Long id, String body) {
        access.writablePatient(id);
        var n = new MedicalNote();
        n.setPatientId(id);
        n.setAuthorId(access.current().getId());
        n.setBody(text(body, 3, 4000, "Observația"));
        notes.save(n);
        return map(
                "id",
                n.getId(),
                "body",
                n.getBody(),
                "author",
                name(n.getAuthorId()),
                "createdAt",
                n.getCreatedAt());
    }

    private Specification<VitalSign> vitalScope(Long patientId, LocalDate from, LocalDate to) {
        List<Long> ids;
        if (patientId != null) {
            access.patient(patientId);
            ids = List.of(patientId);
        } else ids = access.visibleIds();
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException(
                    "Data de început trebuie să fie înaintea datei de sfârșit.");
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(ids.isEmpty() ? cb.disjunction() : root.get("patientId").in(ids));
            if (from != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from.atStartOfDay()));
            if (to != null)
                predicates.add(cb.lessThan(root.get("timestamp"), to.plusDays(1).atStartOfDay()));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    public Map<String, Object> measurements(
            Long id, LocalDate from, LocalDate to, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Paginare invalidă.");
        var result =
                vitals.findAll(
                        vitalScope(id, from, to),
                        PageRequest.of(
                                page, size, Sort.by(Sort.Direction.DESC, "timestamp", "id")));
        var names = new HashMap<Long, String>();
        access.visiblePatients().forEach(p -> names.put(p.getId(), p.getFullName()));
        return map(
                "items",
                result.getContent().stream()
                        .map(v -> vitalView(v, names.get(v.getPatientId())))
                        .toList(),
                "total",
                result.getTotalElements(),
                "pages",
                result.getTotalPages(),
                "page",
                page);
    }

    private Map<String, Object> vitalView(VitalSign v, String patientName) {
        return map(
                "id",
                v.getId(),
                "patientId",
                v.getPatientId(),
                "patientName",
                patientName,
                "heartRate",
                v.getHeartRate(),
                "oxygenLevel",
                v.getOxygenLevel(),
                "temperature",
                v.getTemperature(),
                "bloodPressure",
                v.getBloodPressure(),
                "timestamp",
                v.getTimestamp());
    }

    public String csv(Long id, LocalDate from, LocalDate to) {
        var result =
                vitals.findAll(
                        vitalScope(id, from, to),
                        PageRequest.of(0, 10001, Sort.by(Sort.Direction.DESC, "timestamp", "id")));
        if (result.getTotalElements() > 10000)
            throw new IllegalArgumentException(
                    "Exportul permite cel mult 10.000 de măsurători. Restrânge intervalul.");
        var names = new HashMap<Long, String>();
        access.visiblePatients().forEach(p -> names.put(p.getId(), p.getFullName()));
        var out =
                new StringBuilder(
                        "\uFEFFData,ID pacient,Pacient,Puls BPM,SpO2 %,Tensiune mmHg,Temperatura"
                            + " C\r\n");
        for (var v : result.getContent())
            out.append(csvCell(v.getTimestamp().toString()))
                    .append(',')
                    .append(v.getPatientId())
                    .append(',')
                    .append(csvCell(names.get(v.getPatientId())))
                    .append(',')
                    .append(v.getHeartRate())
                    .append(',')
                    .append(v.getOxygenLevel())
                    .append(',')
                    .append(csvCell(v.getBloodPressure()))
                    .append(',')
                    .append(v.getTemperature())
                    .append("\r\n");
        return out.toString();
    }

    private String csvCell(String s) {
        s = Objects.toString(s, "");
        if (s.matches("^[=+@\\-\t\r].*")) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public List<Map<String, Object>> alertList(String status, Long patientId) {
        List<Long> ids =
                patientId == null
                        ? access.visibleIds()
                        : List.of(access.patient(patientId).getId());
        if (ids.isEmpty()) return List.of();
        var names = new HashMap<Long, String>();
        access.visiblePatients().forEach(p -> names.put(p.getId(), p.getFullName()));
        return alerts.findByPatientIdInOrderByCreatedAtDesc(ids).stream()
                .filter(a -> status == null || status.isBlank() || a.getStatus().equals(status))
                .map(
                        a ->
                                map(
                                        "id",
                                        a.getId(),
                                        "patientId",
                                        a.getPatientId(),
                                        "patientName",
                                        names.get(a.getPatientId()),
                                        "measurementId",
                                        a.getMeasurementId(),
                                        "metric",
                                        a.getMetric(),
                                        "message",
                                        a.getMessage(),
                                        "status",
                                        a.getStatus(),
                                        "createdAt",
                                        a.getCreatedAt(),
                                        "reviewedBy",
                                        a.getReviewedBy() == null ? null : name(a.getReviewedBy()),
                                        "updatedAt",
                                        a.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public Map<String, Object> alertStatus(Long id, String status) {
        access.requireStaff();
        var a =
                alerts.findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Alertă inexistentă."));
        access.patient(a.getPatientId());
        if (!Set.of("VERIFICATA", "INCHISA").contains(Objects.toString(status, "")))
            throw new IllegalArgumentException("Stare invalidă.");
        if ("INCHISA".equals(a.getStatus())
                || ("NOUA".equals(a.getStatus()) && !"VERIFICATA".equals(status)))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Verifică mai întâi alerta; o alertă închisă nu se redeschide.");
        a.setStatus(status);
        a.setReviewedBy(access.current().getId());
        a.setUpdatedAt(LocalDateTime.now());
        alerts.save(a);
        return map("id", id, "status", status);
    }

    @Transactional
    public AlertRule saveRule(Long id, RuleInput in) {
        access.writablePatient(id);
        if (in.heartRateMin() == null
                || in.heartRateMax() == null
                || in.oxygenMin() == null
                || in.temperatureMin() == null
                || in.temperatureMax() == null
                || in.systolicMin() == null
                || in.systolicMax() == null
                || in.diastolicMin() == null
                || in.diastolicMax() == null)
            throw new IllegalArgumentException("Completează toate pragurile.");
        range(in.heartRateMin(), in.heartRateMax(), 1, 300);
        range(in.temperatureMin(), in.temperatureMax(), 20, 50);
        range(in.systolicMin(), in.systolicMax(), 1, 300);
        range(in.diastolicMin(), in.diastolicMax(), 1, 200);
        if (in.oxygenMin() < 0 || in.oxygenMin() > 100)
            throw new IllegalArgumentException("SpO₂ trebuie să fie între 0 și 100.");
        var r = evaluator.rule(id);
        r.setHeartRateMin(in.heartRateMin());
        r.setHeartRateMax(in.heartRateMax());
        r.setOxygenMin(in.oxygenMin());
        r.setTemperatureMin(in.temperatureMin());
        r.setTemperatureMax(in.temperatureMax());
        r.setSystolicMin(in.systolicMin());
        r.setSystolicMax(in.systolicMax());
        r.setDiastolicMin(in.diastolicMin());
        r.setDiastolicMax(in.diastolicMax());
        return rules.save(r);
    }

    private void range(double min, double max, double low, double high) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min < low || max > high || min >= max)
            throw new IllegalArgumentException(
                    "Praguri invalide: minimul trebuie să fie mai mic decât maximul, în limitele"
                        + " formularului.");
    }

    private Map<String, Object> appointmentView(Appointment a) {
        return map(
                "id",
                a.getId(),
                "patientId",
                a.getPatientId(),
                "patientName",
                patients.findById(a.getPatientId()).map(Patient::getFullName).orElse("Pacient"),
                "doctorId",
                a.getDoctorId(),
                "doctorName",
                name(a.getDoctorId()),
                "scheduledAt",
                a.getScheduledAt(),
                "durationMinutes",
                a.getDurationMinutes(),
                "reason",
                a.getReason(),
                "status",
                a.getStatus());
    }

    public List<Map<String, Object>> appointmentList(LocalDate from, LocalDate to) {
        var ids = access.visibleIds();
        return appointments.findAll().stream()
                .filter(a -> ids.contains(a.getPatientId()))
                .filter(a -> from == null || !a.getScheduledAt().toLocalDate().isBefore(from))
                .filter(a -> to == null || !a.getScheduledAt().toLocalDate().isAfter(to))
                .sorted(Comparator.comparing(Appointment::getScheduledAt))
                .map(this::appointmentView)
                .toList();
    }

    @Transactional
    public Map<String, Object> saveAppointment(Long id, AppointmentInput in) {
        var p = access.patient(in.patientId());
        if (p.isArchived()) throw new IllegalArgumentException("Pacientul este arhivat.");
        var actor = access.current();
        var a =
                id == null
                        ? new Appointment()
                        : appointments
                                .findById(id)
                                .orElseThrow(
                                        () ->
                                                new ResponseStatusException(
                                                        HttpStatus.NOT_FOUND,
                                                        "Programare inexistentă."));
        if (id != null) {
            access.patient(a.getPatientId());
            if (!a.getPatientId().equals(p.getId()))
                throw new IllegalArgumentException(
                        "Pacientul unei programări nu poate fi schimbat.");
        }
        String status = in.status() == null ? "PROGRAMATA" : in.status();
        if (!Set.of("PROGRAMATA", "EFECTUATA", "ANULATA").contains(status))
            throw new IllegalArgumentException("Stare invalidă.");
        if (!access.staff(actor) && id != null) {
            if (!"ANULATA".equals(status) || !"PROGRAMATA".equals(a.getStatus()))
                throw new AccessDeniedException("Poți doar anula o programare activă.");
            a.setStatus(status);
            return appointmentView(appointments.save(a));
        }
        if (!access.staff(actor) && !"PROGRAMATA".equals(status))
            throw new AccessDeniedException("Poți doar solicita o programare.");
        if (id == null && !"PROGRAMATA".equals(status))
            throw new IllegalArgumentException("O programare nouă începe în starea Programată.");
        if (p.getDoctorId() == null)
            throw new IllegalArgumentException("Pacientul nu are încă un medic repartizat.");
        doctor(p.getDoctorId());
        if (in.scheduledAt() == null) throw new IllegalArgumentException("Alege data și ora.");
        if ("PROGRAMATA".equals(status) && in.scheduledAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Alege o dată viitoare.");
        if (in.scheduledAt().isAfter(LocalDateTime.now().plusYears(2)))
            throw new IllegalArgumentException("Programările se fac cu maximum doi ani în avans.");
        int duration = in.durationMinutes() == null ? 30 : in.durationMinutes();
        if (duration < 10 || duration > 180)
            throw new IllegalArgumentException("Durata trebuie să fie între 10 și 180 de minute.");
        users.lockById(p.getDoctorId()).orElseThrow();
        if ("PROGRAMATA".equals(status))
            for (var other : appointments.findByDoctorIdAndStatus(p.getDoctorId(), "PROGRAMATA")) {
                if (!Objects.equals(other.getId(), id)
                        && in.scheduledAt()
                                .isBefore(
                                        other.getScheduledAt()
                                                .plusMinutes(other.getDurationMinutes()))
                        && in.scheduledAt().plusMinutes(duration).isAfter(other.getScheduledAt()))
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Medicul are deja o programare în acest interval.");
            }
        a.setPatientId(p.getId());
        a.setDoctorId(p.getDoctorId());
        a.setScheduledAt(in.scheduledAt());
        a.setDurationMinutes(duration);
        a.setReason(text(in.reason(), 3, 500, "Motivul"));
        a.setStatus(status);
        return appointmentView(appointments.saveAndFlush(a));
    }

    public List<Map<String, Object>> staffOptions() {
        access.requireStaff();
        var actor = access.current();
        return users.findAll().stream()
                .filter(
                        u ->
                                u.isEnabled()
                                        && "ROLE_MEDIC".equals(u.getRole())
                                        && (access.admin(actor) || u.getId().equals(actor.getId())))
                .map(
                        u ->
                                map(
                                        "id",
                                        u.getId(),
                                        "fullName",
                                        u.getFullName(),
                                        "username",
                                        u.getUsername()))
                .toList();
    }

    public List<Map<String, Object>> userList() {
        access.requireAdmin();
        return users.findAll().stream()
                .sorted(Comparator.comparing(User::getId))
                .map(
                        u ->
                                map(
                                        "id",
                                        u.getId(),
                                        "username",
                                        u.getUsername(),
                                        "fullName",
                                        u.getFullName(),
                                        "role",
                                        u.getRole(),
                                        "enabled",
                                        u.isEnabled(),
                                        "patientId",
                                        patients.findByUserId(u.getId())
                                                .map(Patient::getId)
                                                .orElse(null)))
                .toList();
    }

    @Transactional
    public Map<String, Object> saveUser(Long id, UserInput in) {
        access.requireAdmin();
        var actor = access.current();
        String role = in.role();
        if (role == null || !Set.of("ROLE_ADMIN", "ROLE_MEDIC", "ROLE_PATIENT").contains(role))
            throw new IllegalArgumentException("Rol invalid.");
        User u;
        if (id == null) {
            String username = text(in.username(), 3, 50, "Utilizatorul");
            if (!username.matches("[A-Za-z0-9_.-]+"))
                throw new IllegalArgumentException(
                        "Utilizatorul poate conține litere, cifre, punct, _ și -.");
            if (users.findByUsername(username).isPresent())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Utilizatorul există deja.");
            String password = in.password() == null ? "" : in.password();
            if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
                throw new IllegalArgumentException(
                        "Parola trebuie să aibă minimum 12 caractere și maximum 72 de octeți"
                            + " UTF-8.");
            u = new User(null, username, encoder.encode(password), "", role);
        } else {
            u =
                    users.findById(id)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND, "Cont inexistent."));
            if (in.password() != null && !in.password().isBlank())
                throw new IllegalArgumentException("Acest formular nu modifică parolele.");
            if (actor.getId().equals(id)
                    && (!"ROLE_ADMIN".equals(role) || Boolean.FALSE.equals(in.enabled())))
                throw new IllegalArgumentException(
                        "Nu îți poți elimina propriul acces de administrator.");
            boolean removingMedic =
                    "ROLE_MEDIC".equals(u.getRole())
                            && (!"ROLE_MEDIC".equals(role) || Boolean.FALSE.equals(in.enabled()));
            if (removingMedic && patients.existsByDoctorId(id))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Repartizează pacienții altui medic înainte de modificarea acestui cont.");
            if (!role.equals(u.getRole()) && patients.findByUserId(id).isPresent())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Dezasociază mai întâi fișa de pacient a acestui cont.");
        }
        u.setFullName(text(in.fullName(), 2, 100, "Numele"));
        u.setRole(role);
        u.setEnabled(in.enabled() == null || in.enabled());
        users.saveAndFlush(u);
        if (id == null && "ROLE_PATIENT".equals(role)) {
            var p = new Patient();
            p.setFullName(u.getFullName());
            p.setUserId(u.getId());
            patients.save(p);
        }
        return map(
                "id",
                u.getId(),
                "username",
                u.getUsername(),
                "role",
                u.getRole(),
                "enabled",
                u.isEnabled());
    }

    public Map<String, Object> dashboard() {
        access.requireStaff();
        var ps = access.visiblePatients();
        var ids = ps.stream().map(Patient::getId).toList();
        var active =
                ids.isEmpty()
                        ? List.<MedicalAlert>of()
                        : alerts.findByPatientIdInOrderByCreatedAtDesc(ids);
        var upcoming =
                appointmentList(LocalDate.now(), null).stream()
                        .filter(
                                a ->
                                        "PROGRAMATA".equals(a.get("status"))
                                                && ((LocalDateTime) a.get("scheduledAt"))
                                                        .isAfter(LocalDateTime.now()))
                        .toList();
        var week = vitals.findAll(vitalScope(null, LocalDate.now().minusDays(6), LocalDate.now()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            var day = LocalDate.now().minusDays(i);
            trend.add(
                    map(
                            "date",
                            day,
                            "count",
                            week.stream()
                                    .filter(v -> v.getTimestamp().toLocalDate().equals(day))
                                    .count()));
        }
        return map(
                "patients",
                ps.stream().filter(p -> !p.isArchived()).count(),
                "measurements",
                vitals.count(vitalScope(null, null, null)),
                "activeAlerts",
                active.stream().filter(a -> !"INCHISA".equals(a.getStatus())).count(),
                "appointments",
                upcoming.size(),
                "latest",
                measurements(null, null, null, 0, 8).get("items"),
                "upcoming",
                upcoming.stream().limit(5).toList(),
                "trend",
                trend);
    }
}
