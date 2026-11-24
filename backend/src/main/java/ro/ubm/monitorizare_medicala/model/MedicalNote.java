package ro.ubm.monitorizare_medicala.model;

import jakarta.persistence.*;

import lombok.*;

@Entity
@Table(name = "medical_notes")
@Getter
@Setter
@NoArgsConstructor
public class MedicalNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 4000)
    private String body;

    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
}
