package ro.ubm.monitorizare_medicala.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ro.ubm.monitorizare_medicala.security.TokenService;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}

    public record UtilizatorCurent(String username, List<String> roluri, Instant expiraLa) {}

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    // AuthenticationManager-ul global, construit de Spring din UtilizatorDetailsService +
    // PasswordEncoder
    public AuthApiController(AuthenticationConfiguration config, TokenService tokenService)
            throws Exception {
        this.authenticationManager = config.getAuthenticationManager();
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        // Parola gresita -> BadCredentialsException -> 401 (metoda invalidLogin)
        Authentication auth =
                authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(
                                req.username(), req.password()));
        return new TokenResponse(
                tokenService.genereaza(auth), "Bearer", tokenService.valabilitateSecunde());
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
    public org.springframework.http.ProblemDetail invalidLogin() {
        return org.springframework.http.ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Utilizator sau parolă incorectă.");
    }

    @GetMapping("/eu")
    public UtilizatorCurent eu(@AuthenticationPrincipal Jwt jwt) {
        return new UtilizatorCurent(
                jwt.getSubject(), jwt.getClaimAsStringList("roles"), jwt.getExpiresAt());
    }
}
