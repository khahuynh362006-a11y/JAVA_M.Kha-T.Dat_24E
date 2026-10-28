package com.khadat.cinema.controller;

import com.khadat.cinema.dto.ApiError;
import com.khadat.cinema.exception.GlobalExceptionHandler;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sanitized fallback for servlet error dispatches outside controller advice. */
@RestController
public class ApiErrorController implements ErrorController {
    private final GlobalExceptionHandler errors;

    public ApiErrorController(GlobalExceptionHandler errors) { this.errors = errors; }

    @RequestMapping("/error")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer number ? number : 404;
        Object original = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = original instanceof String text ? text : request.getRequestURI();
        return errors.standardStatus(status, path);
    }
}
