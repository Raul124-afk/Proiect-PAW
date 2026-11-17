package ro.ubm.monitorizare_medicala;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Punctul de pornire Spring Boot: configurare automată și descoperirea componentelor.
@SpringBootApplication
public class MonitorizareMedicalaApplication {

    public static void main(String[] args) {
        // Creează contextul Spring și pornește serverul web Tomcat integrat.
        SpringApplication.run(MonitorizareMedicalaApplication.class, args);
    }
}
