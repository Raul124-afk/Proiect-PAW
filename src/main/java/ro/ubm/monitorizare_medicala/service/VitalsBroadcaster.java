package ro.ubm.monitorizare_medicala.service;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

import ro.ubm.monitorizare_medicala.model.VitalSign;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

// Componentă Spring care păstrează conexiunile și trimite aceeași actualizare tuturor.
@Component
public class VitalsBroadcaster extends TextWebSocketHandler {
    // Colecție sigură pentru conexiuni accesate simultan de mai multe cereri.
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public VitalsBroadcaster(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Decoratorul împiedică două trimiteri simultane pe aceeași conexiune.
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
        // Socketul este doar pentru citire. Scrierea se face prin REST, cu CSRF.
        session.close(CloseStatus.POLICY_VIOLATION);
    }

    // După logout sesiunea HTTP nu mai autorizează primirea datelor.
    private boolean isAuthenticatedMedic(WebSocketSession socket) {
        try {
            HttpSession session = (HttpSession) socket.getAttributes().get("httpSession");
            SecurityContext context =
                    (SecurityContext)
                            session.getAttribute(
                                    HttpSessionSecurityContextRepository
                                            .SPRING_SECURITY_CONTEXT_KEY);
            if (context == null || context.getAuthentication() == null) {
                return false;
            }
            var authentication = context.getAuthentication();
            if (!authentication.isAuthenticated()) {
                return false;
            }
            for (var authority : authentication.getAuthorities()) {
                if (authority.getAuthority().equals("ROLE_MEDIC")) {
                    return true;
                }
            }
            return false;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    public void broadcast(VitalSign vital) {
        // Jackson transformă obiectul Java în JSON pentru browser.
        TextMessage message = new TextMessage(mapper.writeValueAsString(vital));
        for (WebSocketSession session : sessions.values()) {
            try {
                if (!isAuthenticatedMedic(session)) {
                    session.close(CloseStatus.POLICY_VIOLATION);
                    sessions.remove(session.getId());
                } else if (session.isOpen()) session.sendMessage(message);
            } catch (IOException | RuntimeException ex) {
                sessions.remove(session.getId());
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
