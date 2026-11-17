package ro.ubm.monitorizare_medicala.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import ro.ubm.monitorizare_medicala.model.VitalSign;
import ro.ubm.monitorizare_medicala.service.VitalSignsService;

import java.util.List;
import java.util.Map;

// REST Controller: primește cereri HTTP și returnează date JSON.
@RestController
// Aceeași logică: JWT pe /api, sesiune + CSRF pe /web-api.
@RequestMapping({"/api/vitals", "/web-api/vitals"})
public class VitalSignsController {

    private final VitalSignsService service;

    // Dependency injection: Spring furnizează obiectul service prin constructor.
    public VitalSignsController(VitalSignsService service) {
        this.service = service;
    }

    @GetMapping("/all")
    public List<VitalSign> all() {
        return service.getAllVitals();
    }

    @GetMapping("/patient/{id}")
    public List<VitalSign> history(@PathVariable Long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID-ul pacientului trebuie să fie pozitiv.");
        }
        return service.getPatientHistory(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VitalSign save(@RequestBody VitalInput input) {
        VitalSign measurement = input.toEntity();
        return service.saveVitalSign(measurement);
    }

    // Transformăm eroarea de validare într-un răspuns HTTP 400 pentru formular.
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid(IllegalArgumentException error) {
        return Map.of("message", error.getMessage());
    }
}
