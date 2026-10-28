package com.khadat.cinema.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khadat.cinema.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorWriter {
    private final GlobalExceptionHandler errors;
    private final ObjectMapper json;
    public SecurityErrorWriter(GlobalExceptionHandler errors, ObjectMapper json) { this.errors = errors; this.json = json; }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String code, String safeMessage) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), errors.response(status, code, safeMessage,
            request.getRequestURI(), Map.of()).getBody());
    }
}
