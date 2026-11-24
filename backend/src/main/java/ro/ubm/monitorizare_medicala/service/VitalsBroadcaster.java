package ro.ubm.monitorizare_medicala.service;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

import ro.ubm.monitorizare_medicala.model.*;
import ro.ubm.monitorizare_medicala.repository.*;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VitalsBroadcaster extends TextWebSocketHandler {
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final UserRepository users;
    private final PatientRepository patients;

    public VitalsBroadcaster(
            ObjectMapper mapper, UserRepository users, PatientRepository patients) {
        this.mapper = mapper;
        this.users = users;
        this.patients = patients;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(
                session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5000, 65536));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws IOException {
        session.close(CloseStatus.POLICY_VIOLATION);
    }

    private User recipient(WebSocketSession socket) {
        try {
            var http = (HttpSession) socket.getAttributes().get("httpSession");
            var ctx =
                    (SecurityContext)
                            http.getAttribute(
                                    HttpSessionSecurityContextRepository
                                            .SPRING_SECURITY_CONTEXT_KEY);
            if (ctx == null
                    || ctx.getAuthentication() == null
                    || !ctx.getAuthentication().isAuthenticated()) return null;
            var auth = ctx.getAuthentication();
            var u = users.findByUsername(auth.getName()).orElse(null);
            if (u == null
                    || !u.isEnabled()
                    || !java.util.Set.of("ROLE_MEDIC", "ROLE_ADMIN").contains(u.getRole())
                    || auth.getAuthorities().stream()
                            .noneMatch(a -> a.getAuthority().equals(u.getRole()))) return null;
            return u;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    public void broadcast(VitalSign vital) {
        var message = new TextMessage(mapper.writeValueAsString(vital));
        var p = patients.findById(vital.getPatientId()).orElse(null);
        for (var socket : sessions.values())
            try {
                var u = recipient(socket);
                if (u == null) {
                    socket.close(CloseStatus.POLICY_VIOLATION);
                    sessions.remove(socket.getId());
                } else if (socket.isOpen()
                        && p != null
                        && ("ROLE_ADMIN".equals(u.getRole()) || u.getId().equals(p.getDoctorId())))
                    socket.sendMessage(message);
            } catch (IOException | RuntimeException e) {
                sessions.remove(socket.getId());
                try {
                    socket.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                }
            }
    }
}
