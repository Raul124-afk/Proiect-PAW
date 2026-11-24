package ro.ubm.monitorizare_medicala.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import ro.ubm.monitorizare_medicala.security.ProblemDetailSecurityHandler;

import tools.jackson.databind.ObjectMapper;

//  primul lanț pentru JWT, al doilea pentru pagini și sesiune.
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurity(
            HttpSecurity http,
            JwtAuthenticationConverter converter,
            ProblemDetailSecurityHandler errors)
            throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.POST, "/api/auth/login")
                                        .permitAll()
                                        .requestMatchers("/api/auth/eu")
                                        .authenticated()
                                        .anyRequest()
                                        .hasAnyRole("MEDIC", "ADMIN"))
                .oauth2ResourceServer(
                        rs ->
                                rs.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                                        .authenticationEntryPoint(errors)
                                        .accessDeniedHandler(errors))
                .exceptionHandling(
                        ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // API-ul primește explicit Bearer; nu autentifică folosind cookie-ul browserului.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/login.html",
                                                "/inregistrare",
                                                "/app.css",
                                                "/portal.css",
                                                "/portal.js",
                                                "/favicon.svg",
                                                "/web-api/csrf",
                                                "/error")
                                        .permitAll()
                                        .requestMatchers("/administrare")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                "/cont",
                                                "/programari",
                                                "/web-api/portal/**",
                                                "/web-api/me")
                                        .authenticated()
                                        .anyRequest()
                                        .hasAnyRole("MEDIC", "ADMIN"))
                .formLogin(
                        login ->
                                login.loginPage("/login.html")
                                        .loginProcessingUrl("/login")
                                        .successHandler(
                                                (request, response, authentication) -> {
                                                    boolean medic =
                                                            authentication.getAuthorities().stream()
                                                                    .anyMatch(
                                                                            a ->
                                                                                    a.getAuthority()
                                                                                            .matches(
                                                                                                    "ROLE_(MEDIC|ADMIN)"));
                                                    response.sendRedirect(medic ? "/" : "/cont");
                                                })
                                        .failureUrl("/login.html?error")
                                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login.html?logout"))
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                        (request, response, ex) -> {
                                            String path = request.getRequestURI();
                                            if (path.startsWith("/web-api/")
                                                    || path.startsWith("/ws-medical")) {
                                                response.sendError(401);
                                            } else {
                                                response.sendRedirect("/login.html");
                                            }
                                        }));
        // CSRF rămâne activ pentru formulare și cererile JavaScript autentificate prin sesiune.
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        var encoder =
                (DelegatingPasswordEncoder)
                        PasswordEncoderFactories.createDelegatingPasswordEncoder();
        // Acceptăm și hash-urile BCrypt vechi, fără prefix, păstrând parola contului existent.
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }

    @Bean
    public ProblemDetailSecurityHandler securityErrors(ObjectMapper mapper) {
        return new ProblemDetailSecurityHandler(mapper);
    }
}
