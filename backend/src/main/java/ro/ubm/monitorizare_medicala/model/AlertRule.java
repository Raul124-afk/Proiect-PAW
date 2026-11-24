package ro.ubm.monitorizare_medicala.model;

import jakarta.persistence.*;

import lombok.*;

@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
public class AlertRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long patientId;

    private int heartRateMin = 50;
    private int heartRateMax = 110;
    private int oxygenMin = 94;
    private double temperatureMin = 35.5;
    private double temperatureMax = 38.0;
    private int systolicMin = 90;
    private int systolicMax = 150;
    private int diastolicMin = 50;
    private int diastolicMax = 95;
}
