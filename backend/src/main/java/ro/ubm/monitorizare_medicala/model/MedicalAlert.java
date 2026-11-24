package ro.ubm.monitorizare_medicala.model;

import jakarta.persistence.*;

import lombok.*;

@Entity
@Table(name = "medical_alerts")
@Getter
@Setter
@NoArgsConstructor
public class MedicalAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long measurementId;

    @Column(nullable = false, length = 30)
    private String metric;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(nullable = false, length = 20)
    private String status = "NOUA";

    private Long reviewedBy;
    private java.time.LocalDateTime updatedAt;
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
}
