package ro.ubm.monitorizare_medicala;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import ro.ubm.monitorizare_medicala.model.*;
import ro.ubm.monitorizare_medicala.repository.*;

import tools.jackson.databind.*;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:portaltest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.bootstrap.username=medic",
            "app.bootstrap.password=TestMedical2026!"
        })
class PortalIntegrationTests {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PatientRepository patients;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    static final AtomicInteger sequence = new AtomicInteger();
    static final String PASSWORD = "PortalTests2026!";
    User admin, doctorA, doctorB, patientA, patientB;
    Patient pa, pb;
    HttpClient ac, dc, bc, pc;

    @BeforeEach
    void fixtures() throws Exception {
        int n = sequence.incrementAndGet();
        admin = account("admin" + n, "ROLE_ADMIN");
        doctorA = account("docA" + n, "ROLE_MEDIC");
        doctorB = account("docB" + n, "ROLE_MEDIC");
        patientA = account("patA" + n, "ROLE_PATIENT");
        patientB = account("patB" + n, "ROLE_PATIENT");
        pa = patient("Pacient A " + n, doctorA, patientA, false);
        pb = patient("Pacient B " + n, doctorB, patientB, false);
        ac = login(admin);
        dc = login(doctorA);
        bc = login(doctorB);
        pc = login(patientA);
    }

    User account(String username, String role) {
        return users.saveAndFlush(
                new User(null, username, encoder.encode(PASSWORD), username, role));
    }

    Patient patient(String name, User doctor, User user, boolean demo) {
        var p = new Patient();
        p.setFullName(name);
        p.setDoctorId(doctor.getId());
        p.setUserId(user == null ? null : user.getId());
        p.setDemo(demo);
        return patients.saveAndFlush(p);
    }

    HttpClient client() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    String url(String path) {
        return "http://localhost:" + port + path;
    }

    HttpResponse<String> send(
            HttpClient c, String path, String method, String body, String type, String token)
            throws Exception {
        var req = HttpRequest.newBuilder(URI.create(url(path))).timeout(Duration.ofSeconds(10));
        if (type != null) req.header("Content-Type", type);
        if (token != null) req.header("X-CSRF-TOKEN", token);
        return c.send(
                req.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    String csrf(HttpClient c) throws Exception {
        return mapper.readTree(send(c, "/web-api/csrf", "GET", null, null, null).body())
                .get("token")
                .asText();
    }

    HttpClient login(User user) throws Exception {
        var c = client();
        var r =
                send(
                        c,
                        "/login",
                        "POST",
                        "username="
                                + user.getUsername()
                                + "&password="
                                + URLEncoder.encode(
                                        PASSWORD, java.nio.charset.StandardCharsets.UTF_8),
                        "application/x-www-form-urlencoded",
                        csrf(c));
        assertEquals(302, r.statusCode());
        assertFalse(r.headers().firstValue("location").orElse("").contains("error"));
        return c;
    }

    HttpResponse<String> get(HttpClient c, String path) throws Exception {
        return send(c, path, "GET", null, null, null);
    }

    HttpResponse<String> write(HttpClient c, String path, String method, Object body)
            throws Exception {
        return send(c, path, method, mapper.writeValueAsString(body), "application/json", csrf(c));
    }

    JsonNode json(HttpResponse<String> r, int status) {
        assertEquals(status, r.statusCode(), r.body());
        return mapper.readTree(r.body());
    }

    Map<String, Object> vital(long id, int pulse) {
        return Map.of(
                "patientId",
                id,
                "heartRate",
                pulse,
                "oxygenLevel",
                98,
                "bloodPressure",
                "120/80",
                "temperature",
                36.6);
    }

    Map<String, Object> patientBody(Patient p, Long doctor) {
        var m = new HashMap<String, Object>();
        m.put("fullName", p.getFullName());
        m.put("birthDate", null);
        m.put("sex", "");
        m.put("email", "");
        m.put("phone", "");
        m.put("doctorId", doctor);
        m.put("userId", p.getUserId());
        m.put("demo", p.isDemo());
        return m;
    }

    @Test
    void roleAndPatientBoundariesApplyToEveryDataRoute() throws Exception {
        assertEquals(403, get(dc, "/administrare").statusCode());
        assertEquals(403, get(pc, "/pacienti").statusCode());
        assertEquals(200, get(pc, "/cont").statusCode());
        assertEquals(200, get(pc, "/programari").statusCode());
        assertEquals(403, get(dc, "/web-api/portal/patients/" + pb.getId()).statusCode());
        assertEquals(403, get(pc, "/web-api/portal/patients/" + pb.getId()).statusCode());
        assertEquals(403, get(pc, "/web-api/portal/users").statusCode());
        assertEquals(
                403,
                write(pc, "/web-api/portal/patients", "POST", patientBody(pa, doctorA.getId()))
                        .statusCode());
        assertEquals(403, write(dc, "/web-api/vitals", "POST", vital(pb.getId(), 75)).statusCode());
        json(write(dc, "/web-api/vitals", "POST", vital(pa.getId(), 75)), 201);
        var list = json(get(dc, "/web-api/portal/patients"), 200);
        assertEquals(1, list.size());
        assertEquals(pa.getId().longValue(), list.get(0).get("id").asLong());
        var own = json(get(pc, "/web-api/portal/measurements"), 200);
        assertEquals(1, own.get("total").asInt());
        assertEquals(pa.getId().longValue(), own.get("items").get(0).get("patientId").asLong());
        assertEquals(
                403, get(pc, "/web-api/portal/measurements?patientId=" + pb.getId()).statusCode());
        assertEquals(403, get(dc, "/web-api/vitals/patient/" + pb.getId()).statusCode());
        assertEquals(
                403,
                send(dc, "/web-api/portal/patients", "POST", "{}", "application/json", null)
                        .statusCode());
    }

    @Test
    void patientLifecyclePreservesHistoryAndRejectsInvalidEdits() throws Exception {
        var body = patientBody(pa, doctorA.getId());
        body.put("userId", null);
        body.put("fullName", "Pacient nou");
        body.put("demo", true);
        long id = json(write(dc, "/web-api/portal/patients", "POST", body), 201).get("id").asLong();
        json(write(dc, "/web-api/vitals", "POST", vital(id, 70)), 201);
        json(
                write(
                        dc,
                        "/web-api/portal/patients/" + id + "/notes",
                        "POST",
                        Map.of(
                                "body",
                                "Observație de test cu diacritice <script>alert(1)</script>")),
                201);
        var detail = json(get(dc, "/web-api/portal/patients/" + id), 200);
        assertEquals(1, detail.get("notes").size());
        assertTrue(detail.get("notes").get(0).get("body").asText().contains("<script>"));
        body.put("birthDate", LocalDate.now().plusDays(1).toString());
        assertEquals(400, write(dc, "/web-api/portal/patients/" + id, "PUT", body).statusCode());
        body.put("birthDate", null);
        body.put("doctorId", doctorB.getId());
        assertEquals(403, write(dc, "/web-api/portal/patients/" + id, "PUT", body).statusCode());
        json(
                write(
                        dc,
                        "/web-api/portal/patients/" + id + "/archive?archived=true",
                        "PUT",
                        Map.of()),
                200);
        assertEquals(409, write(dc, "/web-api/vitals", "POST", vital(id, 71)).statusCode());
        assertEquals(1, json(get(dc, "/web-api/portal/patients/" + id), 200).get("vitals").size());
        json(
                write(
                        dc,
                        "/web-api/portal/patients/" + id + "/archive?archived=false",
                        "PUT",
                        Map.of()),
                200);
        json(write(dc, "/web-api/vitals", "POST", vital(id, 72)), 201);
        assertEquals(400, get(dc, "/web-api/portal/measurements?from=invalid").statusCode());
    }

    @Test
    void rulesCreatePersistentAlertsWithControlledTransitions() throws Exception {
        var rules =
                Map.of(
                        "heartRateMin",
                        50,
                        "heartRateMax",
                        80,
                        "oxygenMin",
                        94,
                        "temperatureMin",
                        35.5,
                        "temperatureMax",
                        38.0,
                        "systolicMin",
                        90,
                        "systolicMax",
                        150,
                        "diastolicMin",
                        50,
                        "diastolicMax",
                        95);
        json(write(dc, "/web-api/portal/patients/" + pa.getId() + "/rules", "PUT", rules), 200);
        json(write(dc, "/web-api/vitals", "POST", vital(pa.getId(), 100)), 201);
        var list = json(get(dc, "/web-api/portal/alerts"), 200);
        assertEquals(1, list.size());
        long id = list.get(0).get("id").asLong();
        assertEquals("NOUA", list.get(0).get("status").asText());
        assertEquals(0, json(get(bc, "/web-api/portal/alerts"), 200).size());
        assertEquals(
                403,
                write(
                                pc,
                                "/web-api/portal/alerts/" + id + "/status",
                                "PUT",
                                Map.of("status", "VERIFICATA"))
                        .statusCode());
        assertEquals(
                409,
                write(
                                dc,
                                "/web-api/portal/alerts/" + id + "/status",
                                "PUT",
                                Map.of("status", "INCHISA"))
                        .statusCode());
        json(
                write(
                        dc,
                        "/web-api/portal/alerts/" + id + "/status",
                        "PUT",
                        Map.of("status", "VERIFICATA")),
                200);
        json(
                write(
                        dc,
                        "/web-api/portal/alerts/" + id + "/status",
                        "PUT",
                        Map.of("status", "INCHISA")),
                200);
        assertEquals(
                409,
                write(
                                dc,
                                "/web-api/portal/alerts/" + id + "/status",
                                "PUT",
                                Map.of("status", "VERIFICATA"))
                        .statusCode());
        var invalid = new HashMap<String, Object>(rules);
        invalid.put("heartRateMax", 40);
        assertEquals(
                400,
                write(dc, "/web-api/portal/patients/" + pa.getId() + "/rules", "PUT", invalid)
                        .statusCode());
        assertEquals(
                "INCHISA",
                json(get(pc, "/web-api/portal/alerts"), 200).get(0).get("status").asText());
    }

    @Test
    void appointmentsRejectOverlapsAndRespectPatientPermissions() throws Exception {
        String when = LocalDate.now().plusDays(3).atTime(11, 0).toString();
        var body =
                new HashMap<String, Object>(
                        Map.of(
                                "patientId",
                                pa.getId(),
                                "scheduledAt",
                                when,
                                "durationMinutes",
                                30,
                                "reason",
                                "Control demonstrativ",
                                "status",
                                "PROGRAMATA"));
        long id =
                json(write(pc, "/web-api/portal/appointments", "POST", body), 201)
                        .get("id")
                        .asLong();
        assertEquals(409, write(dc, "/web-api/portal/appointments", "POST", body).statusCode());
        body.put("status", "EFECTUATA");
        assertEquals(
                403, write(pc, "/web-api/portal/appointments/" + id, "PUT", body).statusCode());
        assertEquals(
                403, write(bc, "/web-api/portal/appointments/" + id, "PUT", body).statusCode());
        assertEquals(
                409,
                write(
                                ac,
                                "/web-api/portal/patients/" + pa.getId(),
                                "PUT",
                                patientBody(pa, doctorB.getId()))
                        .statusCode());
        body.put("status", "ANULATA");
        json(write(pc, "/web-api/portal/appointments/" + id, "PUT", body), 200);
        body.put("status", "PROGRAMATA");
        json(write(dc, "/web-api/portal/appointments", "POST", body), 201);
        var ownAppointments = json(get(pc, "/web-api/portal/appointments"), 200);
        assertEquals(2, ownAppointments.size());
        for (var appointment : ownAppointments)
            assertEquals(pa.getId().longValue(), appointment.get("patientId").asLong());
        body.put("scheduledAt", LocalDateTime.now().minusDays(1).toString());
        assertEquals(400, write(dc, "/web-api/portal/appointments", "POST", body).statusCode());
    }

    @Test
    void adminManagesAccountsWithoutExposingHashesOrLeavingStaleAccess() throws Exception {
        String username = "created" + sequence.incrementAndGet();
        var body =
                new HashMap<String, Object>(
                        Map.of(
                                "username",
                                username,
                                "fullName",
                                "Pacient creat",
                                "password",
                                PASSWORD,
                                "role",
                                "ROLE_PATIENT",
                                "enabled",
                                true));
        long id = json(write(ac, "/web-api/portal/users", "POST", body), 201).get("id").asLong();
        assertTrue(patients.findByUserId(id).isPresent());
        assertEquals(409, write(ac, "/web-api/portal/users", "POST", body).statusCode());
        String list = get(ac, "/web-api/portal/users").body();
        assertFalse(list.contains("password"));
        assertFalse(list.contains("bcrypt"));
        assertEquals(403, write(dc, "/web-api/portal/users", "POST", body).statusCode());
        var self = Map.of("fullName", "Admin", "role", "ROLE_MEDIC", "enabled", false);
        assertEquals(
                400, write(ac, "/web-api/portal/users/" + admin.getId(), "PUT", self).statusCode());
        assertEquals(
                409,
                write(
                                ac,
                                "/web-api/portal/users/" + doctorA.getId(),
                                "PUT",
                                Map.of(
                                        "fullName",
                                        "Doctor",
                                        "role",
                                        "ROLE_MEDIC",
                                        "enabled",
                                        false))
                        .statusCode());
        json(
                write(
                        ac,
                        "/web-api/portal/users/" + patientA.getId(),
                        "PUT",
                        Map.of("fullName", "Pacient", "role", "ROLE_PATIENT", "enabled", false)),
                200);
        assertEquals(403, get(pc, "/web-api/portal/me").statusCode());
    }

    @Test
    void exportsAndFiltersNeverIncludeAnotherDoctorsData() throws Exception {
        var body = patientBody(pa, doctorA.getId());
        body.put("fullName", "=SUM(1+1)");
        json(write(dc, "/web-api/portal/patients/" + pa.getId(), "PUT", body), 200);
        json(write(dc, "/web-api/vitals", "POST", vital(pa.getId(), 75)), 201);
        json(write(bc, "/web-api/vitals", "POST", vital(pb.getId(), 82)), 201);
        var csv = get(dc, "/web-api/portal/measurements.csv");
        assertEquals(200, csv.statusCode());
        assertTrue(csv.body().contains("'=SUM(1+1)"));
        assertFalse(csv.body().contains(pb.getFullName()));
        assertTrue(
                csv.headers()
                        .firstValue("content-disposition")
                        .orElseThrow()
                        .contains("attachment"));
        assertEquals(
                403,
                get(dc, "/web-api/portal/measurements.csv?patientId=" + pb.getId()).statusCode());
        var next = LocalDate.now().plusDays(2);
        assertEquals(
                0,
                json(get(dc, "/web-api/portal/measurements?from=" + next), 200)
                        .get("total")
                        .asInt());
        assertEquals(
                400,
                get(dc, "/web-api/portal/measurements?from=" + next + "&to=" + LocalDate.now())
                        .statusCode());
    }

    @Test
    void simulationGeneratesRealStoredMeasurementsAndStops() throws Exception {
        assertEquals(
                400,
                write(
                                dc,
                                "/web-api/portal/simulation/start",
                                "POST",
                                Map.of(
                                        "patientId",
                                        pa.getId(),
                                        "intervalSeconds",
                                        2,
                                        "scenario",
                                        "NORMAL"))
                        .statusCode());
        var demo = patient("Simulator test", doctorA, null, true);
        assertEquals(
                403,
                write(
                                bc,
                                "/web-api/portal/simulation/start",
                                "POST",
                                Map.of(
                                        "patientId",
                                        demo.getId(),
                                        "intervalSeconds",
                                        2,
                                        "scenario",
                                        "NORMAL"))
                        .statusCode());
        json(
                write(
                        dc,
                        "/web-api/portal/simulation/start",
                        "POST",
                        Map.of(
                                "patientId",
                                demo.getId(),
                                "intervalSeconds",
                                2,
                                "scenario",
                                "VARIABIL")),
                200);
        assertEquals(
                409,
                write(
                                dc,
                                "/web-api/portal/simulation/start",
                                "POST",
                                Map.of(
                                        "patientId",
                                        demo.getId(),
                                        "intervalSeconds",
                                        2,
                                        "scenario",
                                        "NORMAL"))
                        .statusCode());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        int count = 0;
        while (System.nanoTime() < deadline) {
            count =
                    json(get(dc, "/web-api/portal/measurements?patientId=" + demo.getId()), 200)
                            .get("total")
                            .asInt();
            if (count > 0) break;
            Thread.sleep(100);
        }
        assertTrue(count > 0, "Schedulerul trebuie să salveze o măsurătoare.");
        json(
                write(dc, "/web-api/portal/simulation/" + demo.getId() + "/stop", "POST", Map.of()),
                200);
        int stoppedCount =
                json(get(dc, "/web-api/portal/measurements?patientId=" + demo.getId()), 200)
                        .get("total")
                        .asInt();
        Thread.sleep(2200);
        assertEquals(
                stoppedCount,
                json(get(dc, "/web-api/portal/measurements?patientId=" + demo.getId()), 200)
                        .get("total")
                        .asInt());
        assertFalse(
                json(get(dc, "/web-api/portal/simulation"), 200).get(0).get("running").asBoolean());
    }

    static class Inbox implements WebSocket.Listener {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        CompletableFuture<Integer> closed = new CompletableFuture<>();
        StringBuilder text = new StringBuilder();

        @Override
        public void onOpen(WebSocket s) {
            s.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket s, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                messages.add(text.toString());
                text.setLength(0);
            }
            s.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket s, int status, String reason) {
            closed.complete(status);
            return null;
        }
    }

    @Test
    void websocketSendsOnlyAssignedPatientsAndHonorsAccountRevocation() throws Exception {
        var a = new Inbox();
        var b = new Inbox();
        var root = new Inbox();
        var wa =
                dc.newWebSocketBuilder()
                        .header("Origin", url(""))
                        .buildAsync(URI.create("ws://localhost:" + port + "/ws-medical"), a)
                        .get(5, TimeUnit.SECONDS);
        var wb =
                bc.newWebSocketBuilder()
                        .header("Origin", url(""))
                        .buildAsync(URI.create("ws://localhost:" + port + "/ws-medical"), b)
                        .get(5, TimeUnit.SECONDS);
        var wr =
                ac.newWebSocketBuilder()
                        .header("Origin", url(""))
                        .buildAsync(URI.create("ws://localhost:" + port + "/ws-medical"), root)
                        .get(5, TimeUnit.SECONDS);
        try {
            json(write(dc, "/web-api/vitals", "POST", vital(pa.getId(), 75)), 201);
            assertNotNull(a.messages.poll(2, TimeUnit.SECONDS));
            assertNotNull(root.messages.poll(2, TimeUnit.SECONDS));
            assertNull(b.messages.poll(300, TimeUnit.MILLISECONDS));
            json(
                    write(
                            ac,
                            "/web-api/portal/patients/" + pa.getId(),
                            "PUT",
                            patientBody(pa, doctorB.getId())),
                    200);
            json(write(bc, "/web-api/vitals", "POST", vital(pa.getId(), 76)), 201);
            assertNotNull(b.messages.poll(2, TimeUnit.SECONDS));
            assertNull(a.messages.poll(300, TimeUnit.MILLISECONDS));
            root.messages.clear();
            json(
                    write(
                            ac,
                            "/web-api/portal/users/" + doctorA.getId(),
                            "PUT",
                            Map.of("fullName", "Doctor A", "role", "ROLE_MEDIC", "enabled", false)),
                    200);
            json(write(ac, "/web-api/vitals", "POST", vital(pa.getId(), 77)), 201);
            assertEquals(1008, a.closed.get(2, TimeUnit.SECONDS));
        } finally {
            wa.abort();
            wb.abort();
            wr.abort();
        }
    }
}
