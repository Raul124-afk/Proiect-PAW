package ro.ubm.monitorizare_medicala.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

// Controller MVC: întoarce numele unui șablon Thymeleaf, nu date JSON.
@Controller
public class PageController {
    @GetMapping({"/", "/index.html"})
    public String dashboard(Principal principal, Model model) {
        model.addAttribute("username", principal.getName());
        return "index";
    }

    @GetMapping("/login.html")
    public String login() {
        return "login";
    }
}
