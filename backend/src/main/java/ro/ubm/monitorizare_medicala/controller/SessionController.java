package ro.ubm.monitorizare_medicala.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

// Două informații folosite de JavaScript: tokenul CSRF și utilizatorul autentificat.
@RestController
public class SessionController {
    @GetMapping("/web-api/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "token",
                token.getToken(),
                "headerName",
                token.getHeaderName(),
                "parameterName",
                token.getParameterName());
    }

    @GetMapping("/web-api/me")
    public Map<String, String> me(Principal principal) {
        return Map.of("username", principal.getName());
    }
}
