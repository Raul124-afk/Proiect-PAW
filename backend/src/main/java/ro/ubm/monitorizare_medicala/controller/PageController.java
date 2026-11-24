package ro.ubm.monitorizare_medicala.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.Arrays;

@Controller
public class PageController {
    private final Environment env;

    public PageController(Environment env) {
        this.env = env;
    }

    @GetMapping({
        "/",
        "/index.html",
        "/pacienti",
        "/pacienti/{id}",
        "/masuratori",
        "/alerte",
        "/programari",
        "/administrare",
        "/simulator",
        "/cont"
    })
    public String portal(HttpServletRequest request, Principal principal, Model model) {
        model.addAttribute("username", principal.getName());
        model.addAttribute("path", request.getRequestURI());
        return "portal";
    }

    @GetMapping("/login.html")
    public String login(Model model) {
        model.addAttribute("demoMode", Arrays.asList(env.getActiveProfiles()).contains("demo"));
        return "login";
    }
}
