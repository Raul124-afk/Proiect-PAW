package ro.ubm.monitorizare_medicala.model;

import jakarta.persistence.*;

import lombok.*;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    private java.time.LocalDate birthDate;

    @Column(length = 20)
    private String sex;

    @Column(length = 120)
    private String email;

    @Column(length = 30)
    private String phone;

    private Long doctorId;

    @Column(unique = true)
    private Long userId;

    private boolean demo;
    private boolean archived;
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
}
