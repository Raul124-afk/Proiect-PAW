package ro.ubm.monitorizare_medicala.service;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ro.ubm.monitorizare_medicala.controller.PortalInputs.SimulationInput;
import ro.ubm.monitorizare_medicala.controller.VitalInput;
import ro.ubm.monitorizare_medicala.repository.UserRepository;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
@EnableScheduling
public class SimulationService {
    private final AccessService access;
    private final VitalSignsService vitals;
    private final UserRepository users;
    private final ConcurrentHashMap<Long, Run> runs = new ConcurrentHashMap<>();

    static class Run {
        final Long patientId, ownerId;
        final String owner, scenario;
        final int interval;
        final LocalDateTime startedAt = LocalDateTime.now();
        volatile LocalDateTime nextAt = startedAt;
        volatile int generated;
        volatile boolean running = true;
        volatile String reason = "";

        Run(Long patientId, Long ownerId, String owner, String scenario, int interval) {
            this.patientId = patientId;
            this.ownerId = ownerId;
            this.owner = owner;
            this.scenario = scenario;
            this.interval = interval;
        }
    }

    public SimulationService(AccessService access, VitalSignsService vitals, UserRepository users) {
        this.access = access;
        this.vitals = vitals;
        this.users = users;
    }

    public Object start(SimulationInput in) {
        var p = access.writablePatient(in.patientId());
        var actor = access.current();
        if (!p.isDemo())
            throw new IllegalArgumentException("Simulatorul poate folosi doar fișe demonstrative.");
        int interval = in.intervalSeconds() == null ? 3 : in.intervalSeconds();
        if (interval < 2 || interval > 30)
            throw new IllegalArgumentException(
                    "Intervalul trebuie să fie între 2 și 30 de secunde.");
        String scenario = in.scenario() == null ? "NORMAL" : in.scenario();
        if (!Set.of("NORMAL", "VARIABIL").contains(scenario))
            throw new IllegalArgumentException("Scenariu invalid.");
        Run run =
                runs.compute(
                        p.getId(),
                        (id, old) -> {
                            if (old != null && old.running)
                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Există deja o simulare activă pentru acest pacient.");
                            return new Run(
                                    id, actor.getId(), actor.getUsername(), scenario, interval);
                        });
        return view(run);
    }

    public Object stop(Long patientId) {
        access.requireStaff();
        access.patient(patientId);
        Run run = runs.get(patientId);
        if (run == null)
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Nu există o simulare pentru acest pacient.");
        synchronized (run) {
            if (!run.ownerId.equals(access.current().getId()) && !access.admin(access.current()))
                throw new AccessDeniedException(
                        "Doar inițiatorul sau administratorul poate opri simularea.");
            run.running = false;
            run.reason = "Oprită manual";
            return view(run);
        }
    }

    public Object status() {
        access.requireStaff();
        var ids = access.visibleIds();
        return runs.values().stream()
                .filter(r -> ids.contains(r.patientId))
                .sorted(Comparator.comparing(r -> r.startedAt))
                .map(this::view)
                .toList();
    }

    private Map<String, Object> view(Run r) {
        return PortalService.map(
                "patientId",
                r.patientId,
                "owner",
                r.owner,
                "scenario",
                r.scenario,
                "intervalSeconds",
                r.interval,
                "generated",
                r.generated,
                "running",
                r.running,
                "startedAt",
                r.startedAt,
                "reason",
                r.reason);
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        for (Run r : runs.values())
            synchronized (r) {
                if (!r.running || r.nextAt.isAfter(LocalDateTime.now())) continue;
                if (r.startedAt.plusMinutes(5).isBefore(LocalDateTime.now())
                        || r.generated >= 100) {
                    r.running = false;
                    r.reason = "Limită de demonstrație atinsă";
                    continue;
                }
                var previous = SecurityContextHolder.getContext();
                try {
                    var u = users.findById(r.ownerId).orElseThrow();
                    if (!u.isEnabled()) throw new IllegalStateException();
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    u.getUsername(),
                                    null,
                                    List.of(new SimpleGrantedAuthority(u.getRole()))));
                    SecurityContextHolder.setContext(context);
                    var random = ThreadLocalRandom.current();
                    boolean spike = "VARIABIL".equals(r.scenario) && r.generated % 4 == 3;
                    var input =
                            new VitalInput(
                                    r.patientId,
                                    spike ? 130 + random.nextInt(12) : 65 + random.nextInt(25),
                                    spike ? 89 : 96 + random.nextInt(4),
                                    spike ? "160/100" : "120/80",
                                    spike
                                            ? 38.5
                                            : Math.round((36.3 + random.nextDouble(.7)) * 10)
                                                    / 10.0);
                    vitals.saveVitalSign(input.toEntity());
                    r.generated++;
                    r.nextAt = LocalDateTime.now().plusSeconds(r.interval);
                } catch (RuntimeException e) {
                    r.running = false;
                    r.reason = "Oprită: accesul sau fișa pacientului s-a modificat.";
                } finally {
                    SecurityContextHolder.setContext(previous);
                }
            }
        runs.entrySet()
                .removeIf(
                        e ->
                                !e.getValue().running
                                        && e.getValue()
                                                .startedAt
                                                .plusHours(1)
                                                .isBefore(LocalDateTime.now()));
    }
}
