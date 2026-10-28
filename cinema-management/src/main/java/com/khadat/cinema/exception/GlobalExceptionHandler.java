package com.khadat.cinema.exception;

import com.khadat.cinema.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) { this.clock = clock; }

    public ResponseEntity<ApiError> response(HttpStatusCode status, String code, String message,
                                              String path, Map<String, List<String>> fields) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new ApiError(
            OffsetDateTime.now(clock), status.value(), code, message, path, fields));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> application(ApiException ex, HttpServletRequest request) {
        return response(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(FieldValidationException.class)
    ResponseEntity<ApiError> fieldValidation(FieldValidationException ex, HttpServletRequest request) {
        return response(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI(), ex.getFieldErrors());
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentWrite(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Dữ liệu đang được thay đổi. Hãy tải lại và thử lại.",
            request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fields.computeIfAbsent(error.getField(), ignored -> new ArrayList<>()).add(
                "Giá trị không hợp lệ."));
        // Never echo rejected values or interpolated messages containing secrets.
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Dữ liệu đầu vào không hợp lệ.",
            request.getRequestURI(), fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraints(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(error -> fields
            .computeIfAbsent(error.getPropertyPath().toString(), ignored -> new ArrayList<>())
            .add("Giá trị không hợp lệ."));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Dữ liệu đầu vào không hợp lệ.",
            request.getRequestURI(), fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        if (ex.isForReturnValue()) {
            return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Có lỗi xử lý yêu cầu.",
                request.getRequestURI(), Map.of());
        }
        Map<String, List<String>> fields = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            fields.put(name == null ? "request" : name, List.of("Giá trị không hợp lệ."));
        });
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Dữ liệu đầu vào không hợp lệ.",
            request.getRequestURI(), fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class, ServletRequestBindingException.class})
    ResponseEntity<ApiError> malformed(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Yêu cầu thiếu dữ liệu hoặc sai định dạng.",
            request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(NoResourceFoundException ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.",
            request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", "Dữ liệu trùng hoặc vi phạm ràng buộc liên kết.",
            request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<ApiError> unavailable(DataAccessResourceFailureException ex, HttpServletRequest request) {
        return application(new DatabaseUnavailableException(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException ex, HttpServletRequest request) {
        return standardStatus(ex.getStatusCode().value(), request.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return standardStatus(405, request.getRequestURI());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> media(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return standardStatus(415, request.getRequestURI());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiError> accept(HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return standardStatus(406, request.getRequestURI());
    }

    /** Shared with the servlet error fallback and future Security error writers. */
    public ResponseEntity<ApiError> standardStatus(int value, String path) {
        return switch (value) {
            case 400 -> response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Yêu cầu không hợp lệ.", path, Map.of());
            case 401 -> response(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Cần đăng nhập.", path, Map.of());
            case 403 -> response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không có quyền thực hiện.", path, Map.of());
            case 404 -> response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.", path, Map.of());
            case 405 -> response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ.", path, Map.of());
            case 406 -> response(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "Kiểu phản hồi yêu cầu không được hỗ trợ.", path, Map.of());
            case 409 -> response(HttpStatus.CONFLICT, "DATA_CONFLICT", "Dữ liệu đang xung đột.", path, Map.of());
            case 415 -> response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Định dạng nội dung không được hỗ trợ.", path, Map.of());
            case 503 -> response(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "Tạm thời không thể truy cập cơ sở dữ liệu.", path, Map.of());
            default -> response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Có lỗi xử lý yêu cầu.", path, Map.of());
        };
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        return standardStatus(500, request.getRequestURI());
    }
}
