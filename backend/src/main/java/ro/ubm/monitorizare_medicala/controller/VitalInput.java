package ro.ubm.monitorizare_medicala.controller;

import ro.ubm.monitorizare_medicala.model.VitalSign;

// DTO = datele primite din formular. Nu conține ID-ul înregistrării sau data creării.
// Un record este o clasă Java compactă pentru transportul datelor.
public record VitalInput(
        Long patientId,
        Integer heartRate,
        Integer oxygenLevel,
        String bloodPressure,
        Double temperature) {

    public VitalSign toEntity() {
        validate();
        return new VitalSign(
                null, patientId, heartRate, oxygenLevel, bloodPressure, temperature, null);
    }

    private void validate() {
        if (patientId == null || patientId <= 0) {
            throw new IllegalArgumentException("ID-ul pacientului trebuie să fie pozitiv.");
        }
        if (heartRate == null || heartRate < 1 || heartRate > 300) {
            throw new IllegalArgumentException("Pulsul trebuie să fie între 1 și 300 BPM.");
        }
        if (oxygenLevel == null || oxygenLevel < 0 || oxygenLevel > 100) {
            throw new IllegalArgumentException("SpO₂ trebuie să fie între 0 și 100%.");
        }
        if (temperature == null
                || !Double.isFinite(temperature)
                || temperature < 20
                || temperature > 50) {
            throw new IllegalArgumentException("Temperatura trebuie să fie între 20 și 50 °C.");
        }
        validateBloodPressure();
    }

    private void validateBloodPressure() {
        // Formatul acceptat: două sau trei cifre, /, două sau trei cifre.
        if (bloodPressure == null || !bloodPressure.matches("[0-9]{2,3}/[0-9]{2,3}")) {
            throw new IllegalArgumentException("Tensiunea trebuie scrisă ca în exemplul 120/80.");
        }
        String[] parts = bloodPressure.split("/");
        int systolic = Integer.parseInt(parts[0]);
        int diastolic = Integer.parseInt(parts[1]);
        if (systolic <= diastolic || systolic > 300 || diastolic > 200) {
            throw new IllegalArgumentException(
                    "Tensiune invalidă: prima valoare trebuie să fie mai mare; maxime tehnice"
                            + " 300/200.");
        }
    }
}
