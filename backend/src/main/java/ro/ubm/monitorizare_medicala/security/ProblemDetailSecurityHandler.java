package ro.ubm.monitorizare_medicala.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

/**
 * Erorile de securitate apar in filtre, INAINTE de controller, deci un @ExceptionHandler din
 * controller nu le vede. Aceasta clasa le scrie in acelasi format ProblemDetail ca restul API-ului.
 *
 * <p>401 Unauthorized = nu stim cine esti (lipseste token-ul sau e invalid/expirat) 403 Forbidden =
 * stim cine esti, dar nu ai voie
 */
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        scrie(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "Neautentificat",
                "Trimiteți un token valid în header-ul Authorization: Bearer <token>");
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        scrie(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "Acces interzis",
                "Contul dumneavoastră nu are dreptul să facă această operație");
    }

    private void scrie(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String titlu,
            String detaliu)
            throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detaliu);
        pd.setTitle(titlu);
        pd.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
