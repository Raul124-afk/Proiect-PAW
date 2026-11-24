package ro.ubm.monitorizare_medicala.service;

import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

import ro.ubm.monitorizare_medicala.model.VitalSign;
import ro.ubm.monitorizare_medicala.repository.VitalSignRepository;

import java.util.List;

@Service
@PreAuthorize("hasAnyRole('MEDIC','ADMIN')")
public class VitalSignsService {
    private final VitalSignRepository repository;
    private final VitalsBroadcaster broadcaster;
    private final AccessService access;
    private final AlertEvaluator evaluator;

    public VitalSignsService(
            VitalSignRepository repository,
            VitalsBroadcaster broadcaster,
            AccessService access,
            AlertEvaluator evaluator) {
        this.repository = repository;
        this.broadcaster = broadcaster;
        this.access = access;
        this.evaluator = evaluator;
    }

    @Transactional
    public VitalSign saveVitalSign(VitalSign measurement) {
        access.writablePatient(measurement.getPatientId());
        VitalSign saved = repository.saveAndFlush(measurement);
        evaluator.evaluate(saved);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        broadcaster.broadcast(saved);
                    }
                });
        return saved;
    }

    public List<VitalSign> getPatientHistory(Long patientId) {
        access.patient(patientId);
        return repository.findTop100ByPatientIdOrderByTimestampDescIdDesc(patientId);
    }

    public List<VitalSign> getAllVitals() {
        var ids = access.visibleIds();
        if (ids.isEmpty()) return List.of();
        return repository
                .findAll(
                        (root, q, cb) -> root.get("patientId").in(ids),
                        PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "timestamp", "id")))
                .getContent();
    }
}
