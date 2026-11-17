package ro.ubm.monitorizare_medicala.service;

import org.springframework.stereotype.Service;

import ro.ubm.monitorizare_medicala.model.VitalSign;
import ro.ubm.monitorizare_medicala.repository.VitalSignRepository;

import java.util.List;

// Service: coordonează salvarea în baza de date și notificarea browserelor.
@Service
@org.springframework.security.access.prepost.PreAuthorize("hasRole('MEDIC')")
public class VitalSignsService {

    private final VitalSignRepository repository;
    private final VitalsBroadcaster broadcaster;

    public VitalSignsService(VitalSignRepository repository, VitalsBroadcaster broadcaster) {
        this.repository = repository;
        this.broadcaster = broadcaster;
    }

    public VitalSign saveVitalSign(VitalSign measurement) {
        // Tranzacția repository-ului se încheie înainte să notificăm browserele.
        VitalSign saved = repository.saveAndFlush(measurement);
        broadcaster.broadcast(saved);
        return saved;
    }

    public List<VitalSign> getPatientHistory(Long patientId) {
        return repository.findTop100ByPatientIdOrderByTimestampDescIdDesc(patientId);
    }

    public List<VitalSign> getAllVitals() {
        return repository.findTop100ByOrderByTimestampDescIdDesc();
    }
}
