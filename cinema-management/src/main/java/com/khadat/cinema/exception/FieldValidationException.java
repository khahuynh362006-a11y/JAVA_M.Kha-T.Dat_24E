package com.khadat.cinema.exception;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class FieldValidationException extends ApiException {
    private final Map<String, List<String>> fieldErrors;
    public FieldValidationException(HttpStatus status, String code, String field, String message) {
        super(status, code, message);
        this.fieldErrors = Map.of(field, List.of(message));
    }
    public Map<String, List<String>> getFieldErrors() { return fieldErrors; }
}
