package ro.ubm.monitorizare_medicala.config;

import jakarta.servlet.http.HttpSession;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

import ro.ubm.monitorizare_medicala.service.VitalsBroadcaster;

import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final VitalsBroadcaster broadcaster;

    public WebSocketConfig(VitalsBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Spring acceptă implicit doar conexiuni de pe aceeași origine.
        registry.addHandler(broadcaster, "/ws-medical")
                .addInterceptors(
                        new HandshakeInterceptor() {
                            public boolean beforeHandshake(
                                    ServerHttpRequest request,
                                    ServerHttpResponse response,
                                    WebSocketHandler handler,
                                    Map<String, Object> attributes) {
                                if (!(request instanceof ServletServerHttpRequest servlet))
                                    return false;
                                HttpSession session = servlet.getServletRequest().getSession(false);
                                if (session == null || servlet.getPrincipal() == null) return false;
                                // Legăm conexiunea WebSocket de sesiunea HTTP creată la login.
                                attributes.put("httpSession", session);
                                return true;
                            }

                            public void afterHandshake(
                                    ServerHttpRequest request,
                                    ServerHttpResponse response,
                                    WebSocketHandler handler,
                                    Exception exception) {}
                        });
    }
}
