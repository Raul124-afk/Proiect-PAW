package ro.ubm.monitorizare_medicala.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ro.ubm.monitorizare_medicala.model.*;
import ro.ubm.monitorizare_medicala.repository.*;

import java.util.*;

@Service
public class AccessService {
    private final UserRepository users;
    private final PatientRepository patients;

    public AccessService(UserRepository users, PatientRepository patients) {
        this.users = users;
        this.patients = patients;
    }

    public User current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new AccessDeniedException("Autentificare necesară.");
        User user =
                users.findByUsername(auth.getName())
                        .orElseThrow(() -> new AccessDeniedException("Cont inexistent."));
        if (!user.isEnabled()
                || auth.getAuthorities().stream()
                        .noneMatch(a -> a.getAuthority().equals(user.getRole())))
            throw new AccessDeniedException(
                    "Accesul contului a fost modificat. Autentifică-te din nou.");
        return user;
    }

    public boolean admin(User u) {
        return "ROLE_ADMIN".equals(u.getRole());
    }

    public boolean staff(User u) {
        return admin(u) || "ROLE_MEDIC".equals(u.getRole());
    }

    public void requireStaff() {
        if (!staff(current())) throw new AccessDeniedException("Acces rezervat medicilor.");
    }

    public void requireAdmin() {
        if (!admin(current())) throw new AccessDeniedException("Acces rezervat administratorului.");
    }

    public Patient patient(Long id) {
        Patient p =
                patients.findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Pacient inexistent."));
        User u = current();
        if (!admin(u)
                && !("ROLE_MEDIC".equals(u.getRole()) && u.getId().equals(p.getDoctorId()))
                && !("ROLE_PATIENT".equals(u.getRole()) && u.getId().equals(p.getUserId())))
            throw new AccessDeniedException("Nu ai acces la acest pacient.");
        return p;
    }

    public Patient writablePatient(Long id) {
        requireStaff();
        Patient p = patient(id);
        if (p.isArchived())
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Fișa este arhivată. Reactiveaz-o înainte de modificare.");
        return p;
    }

    public List<Patient> visiblePatients() {
        User u = current();
        if (admin(u)) return patients.findAll();
        if ("ROLE_MEDIC".equals(u.getRole())) return patients.findByDoctorId(u.getId());
        return patients.findByUserId(u.getId()).map(List::of).orElseGet(List::of);
    }

    public List<Long> visibleIds() {
        return visiblePatients().stream().map(Patient::getId).toList();
    }
}
