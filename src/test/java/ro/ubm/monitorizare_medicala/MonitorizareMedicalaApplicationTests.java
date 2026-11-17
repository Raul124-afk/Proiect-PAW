package ro.ubm.monitorizare_medicala;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import ro.ubm.monitorizare_medicala.model.User;
import ro.ubm.monitorizare_medicala.repository.UserRepository;

import tools.jackson.databind.*;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:medicaltest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
            "spring.datasource.username=${TEST_DB_USERNAME:sa}",
            "spring.datasource.password=${TEST_DB_PASSWORD:}",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.bootstrap.username=medic",
            "app.bootstrap.password=TestMedical2026!"
        })
class MonitorizareMedicalaApplicationTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    String base() {
        return "http://localhost:" + port;
    }

    HttpClient client() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    HttpResponse<String> send(
            HttpClient client, String path, String method, String body, String type, String token)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(URI.create(base() + path)).timeout(Duration.ofSeconds(5));
        if (type != null) builder.header("Content-Type", type);
        if (token != null) builder.header("X-CSRF-TOKEN", token);
        return client.send(
                builder.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    String csrf(HttpClient client) throws Exception {
        return mapper.readTree(send(client, "/web-api/csrf", "GET", null, null, null).body())
                .get("token")
                .asText();
    }

    HttpClient login(String username, String password) throws Exception {
        var client = client();
        var response =
                send(
                        client,
                        "/login",
                        "POST",
                        "username="
                                + username
                                + "&password="
                                + URLEncoder.encode(
                                        password, java.nio.charset.StandardCharsets.UTF_8),
                        "application/x-www-form-urlencoded",
                        csrf(client));
        assertEquals(302, response.statusCode());
        assertFalse(response.headers().firstValue("location").orElseThrow().contains("error"));
        return client;
    }

    String input(long patient) {
        return "{\"patientId\":"
                + patient
                + ",\"heartRate\":75,\"oxygenLevel\":98,\"bloodPressure\":\"120/80\",\"temperature\":36.6}";
    }

    @Test
    void loginFormProvidesCsrfWithoutJavascript() throws Exception {
        var client = client();
        var page = send(client, "/login.html", "GET", null, null, null);
        assertEquals(200, page.statusCode());
        assertFalse(page.body().contains("login.js"));
        var token =
                java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
                        .matcher(page.body());
        assertTrue(token.find(), "Thymeleaf trebuie să completeze tokenul CSRF");
        String form =
                "username=medic&password=TestMedical2026%21&_csrf="
                        + URLEncoder.encode(
                                token.group(1), java.nio.charset.StandardCharsets.UTF_8);
        var result =
                send(client, "/login", "POST", form, "application/x-www-form-urlencoded", null);
        assertEquals(302, result.statusCode());
        assertFalse(result.headers().firstValue("location").orElseThrow().contains("error"));
        assertEquals(200, send(client, "/web-api/me", "GET", null, null, null).statusCode());
    }

    @Test
    void thymeleafRendersAuthenticatedPageAndCsrf() throws Exception {
        var client = login("medic", "TestMedical2026!");
        var page = send(client, "/", "GET", null, null, null);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("id=\"user\">medic</span>"));
        assertTrue(page.body().contains("name=\"_csrf\""));
        assertFalse(page.body().contains("th:text"));
    }

    @Test
    void anonymousCannotReadOrOpenSocket() throws Exception {
        var client = client();
        assertEquals(
                401, send(client, "/web-api/vitals/all", "GET", null, null, null).statusCode());
        assertEquals(401, send(client, "/ws-medical", "GET", null, null, null).statusCode());
        assertEquals(302, send(client, "/", "GET", null, null, null).statusCode());
    }

    @Test
    void wrongPasswordAndRoleAreRejected() throws Exception {
        var client = client();
        var bad =
                send(
                        client,
                        "/login",
                        "POST",
                        "username=medic&password=wrong",
                        "application/x-www-form-urlencoded",
                        csrf(client));
        assertTrue(bad.headers().firstValue("location").orElseThrow().contains("error"));
        users.save(
                new User(null, "patient", encoder.encode("Patient2026!"), "Test", "ROLE_PATIENT"));
        var patient = login("patient", "Patient2026!");
        assertEquals(
                403, send(patient, "/web-api/vitals/all", "GET", null, null, null).statusCode());
        assertEquals(403, send(patient, "/ws-medical", "GET", null, null, null).statusCode());
        assertNotEquals(
                "TestMedical2026!", users.findByUsername("medic").orElseThrow().getPassword());
    }

    @Test
    void savesValidatesFiltersAndProtectsCsrf() throws Exception {
        var client = login("medic", "TestMedical2026!");
        var token = csrf(client);
        assertEquals(
                403,
                send(client, "/web-api/vitals", "POST", input(7001), "application/json", null)
                        .statusCode());
        var created =
                send(client, "/web-api/vitals", "POST", input(7001), "application/json", token);
        assertEquals(201, created.statusCode(), created.body());
        var saved = mapper.readTree(created.body());
        assertTrue(saved.get("id").asLong() > 0);
        assertNotNull(saved.get("timestamp"));
        var history =
                mapper.readTree(
                        send(client, "/web-api/vitals/patient/7001", "GET", null, null, null)
                                .body());
        assertEquals(1, history.size());
        assertEquals(saved.get("id"), history.get(0).get("id"));
        assertEquals(
                0,
                mapper.readTree(
                                send(
                                                client,
                                                "/web-api/vitals/patient/7002",
                                                "GET",
                                                null,
                                                null,
                                                null)
                                        .body())
                        .size());
        for (String invalid :
                new String[] {
                    "{}",
                    input(-1),
                    input(7001).replace("98", "101"),
                    input(7001).replace("120/80", "80/120"),
                    input(7001).replace("36.6", "99"),
                    input(7001).replace("75", "null")
                })
            assertEquals(
                    400,
                    send(client, "/web-api/vitals", "POST", invalid, "application/json", token)
                            .statusCode(),
                    invalid);
        assertEquals(
                400,
                send(client, "/web-api/vitals/patient/0", "GET", null, null, null).statusCode());
        assertEquals(403, send(client, "/logout", "POST", null, null, null).statusCode());
        assertEquals(302, send(client, "/logout", "POST", null, null, token).statusCode());
        assertEquals(
                401, send(client, "/web-api/vitals/all", "GET", null, null, null).statusCode());
    }

    static class Inbox implements WebSocket.Listener {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        CompletableFuture<Integer> closed = new CompletableFuture<>();
        StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                messages.add(buffer.toString());
                buffer.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
            closed.complete(status);
            return null;
        }
    }

    WebSocket connect(HttpClient client, Inbox inbox) throws Exception {
        return client.newWebSocketBuilder()
                .header("Origin", base())
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + port + "/ws-medical"), inbox)
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void broadcastsToTwoClientsAndStopsAfterLogout() throws Exception {
        var first = login("medic", "TestMedical2026!");
        var second = login("medic", "TestMedical2026!");
        var a = new Inbox();
        var b = new Inbox();
        var ws1 = connect(first, a);
        var ws2 = connect(second, b);
        try {
            var saved =
                    send(
                            first,
                            "/web-api/vitals",
                            "POST",
                            input(8001),
                            "application/json",
                            csrf(first));
            assertEquals(201, saved.statusCode());
            assertEquals(
                    mapper.readTree(saved.body()),
                    mapper.readTree(a.messages.poll(5, TimeUnit.SECONDS)));
            assertEquals(
                    mapper.readTree(saved.body()),
                    mapper.readTree(b.messages.poll(5, TimeUnit.SECONDS)));
            send(second, "/logout", "POST", null, null, csrf(second));
            assertEquals(
                    201,
                    send(
                                    first,
                                    "/web-api/vitals",
                                    "POST",
                                    input(8002),
                                    "application/json",
                                    csrf(first))
                            .statusCode());
            assertEquals(1008, b.closed.get(5, TimeUnit.SECONDS));
            assertTrue(b.messages.isEmpty());
        } finally {
            ws1.abort();
            ws2.abort();
        }
    }

    @Test
    void foreignOriginAndSocketWritesAreRejected() throws Exception {
        var client = login("medic", "TestMedical2026!");
        var exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client.newWebSocketBuilder()
                                        .header("Origin", "https://evil.example")
                                        .buildAsync(
                                                URI.create(
                                                        "ws://localhost:" + port + "/ws-medical"),
                                                new Inbox())
                                        .get(5, TimeUnit.SECONDS));
        assertInstanceOf(WebSocketHandshakeException.class, exception.getCause());
        var inbox = new Inbox();
        var ws = connect(client, inbox);
        try {
            ws.sendText(input(9001), true).join();
            assertEquals(1008, inbox.closed.get(5, TimeUnit.SECONDS));
        } finally {
            ws.abort();
        }
    }

    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
    @Autowired ro.ubm.monitorizare_medicala.service.VitalSignsService vitalsService;

    HttpResponse<String> bearer(String path, String method, String body, String token)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body))
                        .build();
        return client().send(request, HttpResponse.BodyHandlers.ofString());
    }

    String jwtLogin(String username, String password) throws Exception {
        var response =
                send(
                        client(),
                        "/api/auth/login",
                        "POST",
                        mapper.writeValueAsString(
                                java.util.Map.of("username", username, "password", password)),
                        "application/json",
                        null);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(
                response.headers().allValues("set-cookie").isEmpty(),
                "API-ul nu trebuie să creeze sesiune");
        var body = mapper.readTree(response.body());
        assertEquals("Bearer", body.get("tokenType").asText());
        assertEquals(3600, body.get("expiresIn").asLong());
        return body.get("accessToken").asText();
    }

    @Test
    void jwtLoginAndApiAreStatelessAndSeparateFromWebSession() throws Exception {
        var token = jwtLogin("medic", "TestMedical2026!");
        var me = bearer("/api/auth/eu", "GET", null, token);
        assertEquals(200, me.statusCode());
        assertEquals("medic", mapper.readTree(me.body()).get("username").asText());
        assertEquals("MEDIC", mapper.readTree(me.body()).get("roluri").get(0).asText());
        assertEquals(1, mapper.readTree(me.body()).get("roluri").size());
        assertEquals(200, bearer("/api/vitals/all", "GET", null, token).statusCode());
        var saved = bearer("/api/vitals", "POST", input(9101), token);
        assertEquals(201, saved.statusCode(), saved.body());
        assertEquals(
                1,
                mapper.readTree(bearer("/api/vitals/patient/9101", "GET", null, token).body())
                        .size());
        assertEquals(401, bearer("/web-api/vitals/all", "GET", null, token).statusCode());
        assertEquals(401, bearer("/ws-medical", "GET", null, token).statusCode());
        var session = login("medic", "TestMedical2026!");
        assertEquals(401, send(session, "/api/vitals/all", "GET", null, null, null).statusCode());
        assertEquals(401, send(session, "/api/auth/eu", "GET", null, null, null).statusCode());
    }

    @Test
    void jwtRejectsInvalidCredentialsExpiredTamperedAndWrongIssuerTokens() throws Exception {
        assertEquals(
                401,
                send(
                                client(),
                                "/api/auth/login",
                                "POST",
                                "{\"username\":\"medic\",\"password\":\"wrong\"}",
                                "application/json",
                                null)
                        .statusCode());
        assertEquals(
                400,
                send(client(), "/api/auth/login", "POST", "{}", "application/json", null)
                        .statusCode());
        String valid = jwtLogin("medic", "TestMedical2026!");
        String[] parts = valid.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        var tampered = bearer("/api/vitals/all", "GET", null, String.join(".", parts));
        assertEquals(401, tampered.statusCode());
        assertTrue(
                tampered.headers()
                        .firstValue("content-type")
                        .orElseThrow()
                        .contains("application/problem+json"));
        var now = java.time.Instant.now();
        for (boolean expired : new boolean[] {true, false}) {
            var claims =
                    org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                            .subject("medic")
                            .issuer(expired ? "monitorizare-medicala" : "wrong-app")
                            .issuedAt(now.minusSeconds(7200))
                            .expiresAt(expired ? now.minusSeconds(3600) : now.plusSeconds(3600))
                            .claim("roles", java.util.List.of("MEDIC"))
                            .build();
            var header =
                    org.springframework.security.oauth2.jwt.JwsHeader.with(
                                    org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                            .build();
            var token =
                    jwtEncoder
                            .encode(
                                    org.springframework.security.oauth2.jwt.JwtEncoderParameters
                                            .from(header, claims))
                            .getTokenValue();
            assertEquals(401, bearer("/api/vitals/all", "GET", null, token).statusCode());
        }
    }

    @Test
    void registrationCannotEscalateRoleAndPatientCannotReadMeasurements() throws Exception {
        var browser = client();
        String form =
                "nume=Pacient+Test&username=registered&parola=Patient2026%21%21&confirmareParola=Patient2026%21%21&role=ROLE_MEDIC";
        assertEquals(200, send(browser, "/inregistrare", "GET", null, null, null).statusCode());
        assertEquals(
                403,
                send(
                                browser,
                                "/inregistrare",
                                "POST",
                                form,
                                "application/x-www-form-urlencoded",
                                null)
                        .statusCode());
        assertEquals(
                302,
                send(
                                browser,
                                "/inregistrare",
                                "POST",
                                form,
                                "application/x-www-form-urlencoded",
                                csrf(browser))
                        .statusCode());
        var user = users.findByUsername("registered").orElseThrow();
        assertEquals("ROLE_PATIENT", user.getRole());
        assertTrue(user.getPassword().startsWith("{bcrypt}"));
        assertTrue(encoder.matches("Patient2026!!", user.getPassword()));
        assertEquals(
                200,
                send(
                                browser,
                                "/inregistrare",
                                "POST",
                                form,
                                "application/x-www-form-urlencoded",
                                csrf(browser))
                        .statusCode());
        var patient = login("registered", "Patient2026!!");
        assertEquals(200, send(patient, "/cont", "GET", null, null, null).statusCode());
        assertEquals(403, send(patient, "/", "GET", null, null, null).statusCode());
        assertEquals(
                403, send(patient, "/web-api/vitals/all", "GET", null, null, null).statusCode());
        var token = jwtLogin("registered", "Patient2026!!");
        assertEquals(200, bearer("/api/auth/eu", "GET", null, token).statusCode());
        var denied = bearer("/api/vitals/all", "GET", null, token);
        assertEquals(403, denied.statusCode());
        assertEquals(403, mapper.readTree(denied.body()).get("status").asInt());
        assertEquals(403, bearer("/api/vitals", "POST", input(9201), token).statusCode());
    }

    @Test
    void registrationValidatesConfirmationAndUtf8Length() throws Exception {
        var browser = client();
        for (String form :
                new String[] {
                    "nume=Test&username=badconfirm&parola=Patient2026%21%21&confirmareParola=Different2026%21",
                    "nume=Test&username=shortpass&parola=short&confirmareParola=short",
                    "nume=Test&username=unicodepass&parola="
                            + URLEncoder.encode(
                                    "ă".repeat(40), java.nio.charset.StandardCharsets.UTF_8)
                            + "&confirmareParola="
                            + URLEncoder.encode(
                                    "ă".repeat(40), java.nio.charset.StandardCharsets.UTF_8)
                }) {
            var result =
                    send(
                            browser,
                            "/inregistrare",
                            "POST",
                            form,
                            "application/x-www-form-urlencoded",
                            csrf(browser));
            assertEquals(200, result.statusCode());
            assertTrue(result.body().contains("role=\"alert\""));
        }
        assertTrue(users.findByUsername("badconfirm").isEmpty());
        assertTrue(users.findByUsername("shortpass").isEmpty());
        assertTrue(users.findByUsername("unicodepass").isEmpty());
    }

    @Test
    void legacyBcryptPasswordStillWorksForWebAndApi() throws Exception {
        users.save(
                new User(
                        null,
                        "legacy",
                        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                                .encode("Legacy2026!!"),
                        "Vechi",
                        "ROLE_MEDIC"));
        var browser = login("legacy", "Legacy2026!!");
        assertEquals(200, send(browser, "/", "GET", null, null, null).statusCode());
        assertEquals(
                200,
                bearer("/api/vitals/all", "GET", null, jwtLogin("legacy", "Legacy2026!!"))
                        .statusCode());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "PATIENT")
    void methodSecurityProtectsServiceEvenWithoutHttp() {
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> vitalsService.getAllVitals());
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> vitalsService.getPatientHistory(1L));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        vitalsService.saveVitalSign(
                                new ro.ubm.monitorizare_medicala.model.VitalSign()));
    }
}
