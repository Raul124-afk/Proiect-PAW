package ro.ubm.monitorizare_medicala.service;

import org.springframework.stereotype.Service;

import ro.ubm.monitorizare_medicala.model.*;
import ro.ubm.monitorizare_medicala.repository.*;

@Service
public class AlertEvaluator {
    private final AlertRuleRepository rules;
    private final MedicalAlertRepository alerts;

    public AlertEvaluator(AlertRuleRepository rules, MedicalAlertRepository alerts) {
        this.rules = rules;
        this.alerts = alerts;
    }

    public AlertRule rule(Long patientId) {
        return rules.findByPatientId(patientId)
                .orElseGet(
                        () -> {
                            var r = new AlertRule();
                            r.setPatientId(patientId);
                            return r;
                        });
    }

    public void evaluate(VitalSign v) {
        var r = rule(v.getPatientId());
        if (v.getHeartRate() < r.getHeartRateMin() || v.getHeartRate() > r.getHeartRateMax())
            add(
                    v,
                    "PULS",
                    "Puls: "
                            + v.getHeartRate()
                            + " BPM, în afara intervalului configurat "
                            + r.getHeartRateMin()
                            + "–"
                            + r.getHeartRateMax()
                            + ".");
        if (v.getOxygenLevel() < r.getOxygenMin())
            add(
                    v,
                    "OXIGEN",
                    "SpO₂: "
                            + v.getOxygenLevel()
                            + "%, sub limita configurată de "
                            + r.getOxygenMin()
                            + "%.");
        if (v.getTemperature() < r.getTemperatureMin()
                || v.getTemperature() > r.getTemperatureMax())
            add(
                    v,
                    "TEMPERATURA",
                    "Temperatură: "
                            + v.getTemperature()
                            + " °C, în afara intervalului configurat "
                            + r.getTemperatureMin()
                            + "–"
                            + r.getTemperatureMax()
                            + ".");
        var bp = v.getBloodPressure().split("/");
        int s = Integer.parseInt(bp[0]), d = Integer.parseInt(bp[1]);
        if (s < r.getSystolicMin()
                || s > r.getSystolicMax()
                || d < r.getDiastolicMin()
                || d > r.getDiastolicMax())
            add(
                    v,
                    "TENSIUNE",
                    "Tensiune: "
                            + v.getBloodPressure()
                            + " mmHg, în afara intervalelor configurate.");
    }

    private void add(VitalSign v, String metric, String message) {
        var a = new MedicalAlert();
        a.setPatientId(v.getPatientId());
        a.setMeasurementId(v.getId());
        a.setMetric(metric);
        a.setMessage(message);
        alerts.save(a);
    }
}
