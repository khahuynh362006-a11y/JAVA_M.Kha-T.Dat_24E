package com.khadat.cinema.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException() {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.");
    }
}
