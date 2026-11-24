package ro.ubm.monitorizare_medicala.controller;

import jakarta.validation.Valid;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import ro.ubm.monitorizare_medicala.service.UtilizatorService;

@Controller
public class AuthWebController {
    private final UtilizatorService users;

    public AuthWebController(UtilizatorService users) {
        this.users = users;
    }

    @GetMapping("/inregistrare")
    public String formular(Model model) {
        model.addAttribute("cont", new InregistrareForm());
        return "inregistrare";
    }

    @PostMapping("/inregistrare")
    public String inregistreaza(
            @Valid @ModelAttribute("cont") InregistrareForm form, BindingResult errors) {
        if (form.getParola() != null && !form.getParola().equals(form.getConfirmareParola())) {
            errors.rejectValue("confirmareParola", "diferit", "Parolele nu coincid.");
        }
        if (errors.hasErrors()) return "inregistrare";
        try {
            users.inregistreaza(form.getNume(), form.getUsername(), form.getParola());
        } catch (IllegalArgumentException ex) {
            errors.reject("invalid", ex.getMessage());
            return "inregistrare";
        } catch (DataIntegrityViolationException ex) {
            // Acoperă și două înregistrări simultane cu același nume.
            errors.rejectValue("username", "duplicat", "Numele de utilizator este deja folosit.");
            return "inregistrare";
        }
        return "redirect:/login.html?inregistrat";
    }
}
