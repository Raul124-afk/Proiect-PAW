package ro.ubm.monitorizare_medicala.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import ro.ubm.monitorizare_medicala.model.User;
import ro.ubm.monitorizare_medicala.repository.UserRepository;

// Creează contul inițial la pornire doar dacă avem parolă și contul nu există.
@Configuration
public class BootstrapUser {
    @Bean
    @org.springframework.core.annotation.Order(10)
    CommandLineRunner createMedic(
            UserRepository users,
            PasswordEncoder encoder,
            @Value("${app.bootstrap.username:medic}") String username,
            @Value("${app.bootstrap.password:}") String password) {
        return args -> {
            if (!password.isBlank() && users.findByUsername(username).isEmpty()) {
                if (password.length() < 12)
                    throw new IllegalArgumentException(
                            "Parola inițială trebuie să aibă minimum 12 caractere.");
                users.save(
                        new User(
                                null,
                                username,
                                encoder.encode(password),
                                "Medic demonstrativ",
                                "ROLE_MEDIC"));
            }
        };
    }
}
