package ro.ubm.monitorizare_medicala.model;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

// Entitate JPA: un obiect corespunde unui rând în tabelul vital_signs.
@Entity
@Table(
        name = "vital_signs",
        indexes = @Index(name = "idx_vitals_patient_timestamp", columnList = "patientId,timestamp"))
// Lombok generează getter-ele și setter-ele la compilare.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VitalSign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long patientId;

    private int heartRate; // Puls (BPM)
    private int oxygenLevel; // SpO2 (%)
    private String bloodPressure; // ex: "120/80"
    private double temperature; // Temperatura (°C)

    private LocalDateTime timestamp;

    // JPA apelează metoda înaintea primei salvări.
    @PrePersist
    public void onCreate() {
        this.timestamp = LocalDateTime.now();
    }
}
