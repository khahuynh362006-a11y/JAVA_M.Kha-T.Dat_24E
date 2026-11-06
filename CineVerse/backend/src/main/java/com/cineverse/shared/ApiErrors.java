package com.cineverse.shared;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    void disconnected(org.springframework.web.context.request.async.AsyncRequestNotUsableException e){LoggerFactory.getLogger(getClass()).debug("Client disconnected before response completed: {}",e.getClass().getSimpleName());}
    @ExceptionHandler({
        org.springframework.web.servlet.resource.NoResourceFoundException.class,
        org.springframework.dao.EmptyResultDataAccessException.class
    })
    ResponseEntity<?> missing(Exception e, HttpServletRequest r) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy dữ liệu.", r, Map.of());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<?> upload(Exception e, HttpServletRequest r) {
        return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "Tệp tải lên quá lớn.",
                r,
                Map.of());
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> domain(ApiException e, HttpServletRequest r) {
        return error(e.status, e.code, e.getMessage(), r, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Vui lòng kiểm tra thông tin đã nhập.",
                r,
                fields);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception e, HttpServletRequest r) {
        return error(
                HttpStatus.CONFLICT,
                "CONFLICT",
                "Dữ liệu trùng hoặc đang được sử dụng. Hãy tải lại và kiểm tra.",
                r,
                Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> forbidden(Exception e, HttpServletRequest r) {
        return error(
                HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền truy cập.", r, Map.of());
    }

    @ExceptionHandler({
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        jakarta.validation.ConstraintViolationException.class
    })
    ResponseEntity<?> malformed(Exception e, HttpServletRequest r) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Dữ liệu yêu cầu không hợp lệ.",
                r,
                Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e, HttpServletRequest r) {
        LoggerFactory.getLogger(getClass())
                .error("Unhandled API error at {}", r.getRequestURI(), e);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Có lỗi hệ thống. Vui lòng thử lại.",
                r,
                Map.of());
    }

    private ResponseEntity<?> error(
            HttpStatus s,
            String code,
            String message,
            HttpServletRequest r,
            Map<String, String> fields) {
        return ResponseEntity.status(s)
                .body(
                        Map.of(
                                "timestamp",
                                Instant.now(),
                                "status",
                                s.value(),
                                "errorCode",
                                code,
                                "message",
                                message,
                                "path",
                                r.getRequestURI(),
                                "validationErrors",
                                fields));
    }
}
