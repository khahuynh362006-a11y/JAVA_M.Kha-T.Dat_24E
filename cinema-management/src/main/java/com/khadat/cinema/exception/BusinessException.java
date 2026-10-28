package com.khadat.cinema.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends ApiException {
    public BusinessException(String code, String safeMessage) {
        super(HttpStatus.CONFLICT, code, safeMessage);
    }
}
