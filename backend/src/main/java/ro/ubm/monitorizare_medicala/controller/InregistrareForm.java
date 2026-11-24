package ro.ubm.monitorizare_medicala.controller;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class InregistrareForm {
    @NotBlank(message = "Numele este obligatoriu.")
    @Size(max = 100, message = "Numele poate avea cel mult 100 de caractere.")
    private String nume;

    @NotBlank(message = "Utilizatorul este obligatoriu.")
    @Pattern(
            regexp = "[a-zA-Z0-9._-]{3,50}",
            message = "Folosește 3–50 de litere, cifre, punct, cratimă sau underscore.")
    private String username;

    @NotBlank(message = "Parola este obligatorie.")
    @Size(min = 12, max = 72, message = "Parola trebuie să aibă 12–72 de caractere.")
    private String parola;

    @NotBlank(message = "Confirmă parola.")
    private String confirmareParola;
}
