package ro.ubm.monitorizare_medicala.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import ro.ubm.monitorizare_medicala.controller.PortalInputs.*;
import ro.ubm.monitorizare_medicala.service.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/web-api/portal")
public class PortalController {
    private final PortalService portal;
    private final SimulationService simulation;

    public PortalController(PortalService portal, SimulationService simulation) {
        this.portal = portal;
        this.simulation = simulation;
    }

    @GetMapping("/me")
    public Object me() {
        return portal.me();
    }

    @GetMapping("/dashboard")
    public Object dashboard() {
        return portal.dashboard();
    }

    @GetMapping("/patients")
    public Object patients(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "false") boolean archived) {
        return portal.patientList(q, archived);
    }

    @GetMapping("/patients/{id}")
    public Object patient(@PathVariable Long id) {
        return portal.detail(id);
    }

    @PostMapping("/patients")
    @ResponseStatus(HttpStatus.CREATED)
    public Object createPatient(@RequestBody PatientInput in) {
        return portal.savePatient(null, in);
    }

    @PutMapping("/patients/{id}")
    public Object updatePatient(@PathVariable Long id, @RequestBody PatientInput in) {
        return portal.savePatient(id, in);
    }

    @PutMapping("/patients/{id}/archive")
    public Object archive(@PathVariable Long id, @RequestParam boolean archived) {
        return portal.archivePatient(id, archived);
    }

    @PostMapping("/patients/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public Object note(@PathVariable Long id, @RequestBody NoteInput in) {
        return portal.addNote(id, in.body());
    }

    @PutMapping("/patients/{id}/rules")
    public Object rules(@PathVariable Long id, @RequestBody RuleInput in) {
        return portal.saveRule(id, in);
    }

    @GetMapping("/measurements")
    public Object measurements(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return portal.measurements(patientId, from, to, page, size);
    }

    @GetMapping("/measurements.csv")
    public ResponseEntity<byte[]> csv(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=masuratori.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(portal.csv(patientId, from, to).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/alerts")
    public Object alerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long patientId) {
        return portal.alertList(status, patientId);
    }

    @PutMapping("/alerts/{id}/status")
    public Object status(@PathVariable Long id, @RequestBody StatusInput in) {
        return portal.alertStatus(id, in.status());
    }

    @GetMapping("/appointments")
    public Object appointments(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return portal.appointmentList(from, to);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public Object createAppointment(@RequestBody AppointmentInput in) {
        return portal.saveAppointment(null, in);
    }

    @PutMapping("/appointments/{id}")
    public Object updateAppointment(@PathVariable Long id, @RequestBody AppointmentInput in) {
        return portal.saveAppointment(id, in);
    }

    @GetMapping("/doctors")
    public Object doctors() {
        return portal.staffOptions();
    }

    @GetMapping("/users")
    public Object users() {
        return portal.userList();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Object createUser(@RequestBody UserInput in) {
        return portal.saveUser(null, in);
    }

    @PutMapping("/users/{id}")
    public Object updateUser(@PathVariable Long id, @RequestBody UserInput in) {
        return portal.saveUser(id, in);
    }

    @GetMapping("/simulation")
    public Object simulation() {
        return simulation.status();
    }

    @PostMapping("/simulation/start")
    public Object start(@RequestBody SimulationInput in) {
        return simulation.start(in);
    }

    @PostMapping("/simulation/{id}/stop")
    public Object stop(@PathVariable Long id) {
        return simulation.stop(id);
    }
}
