package com.khadat.cinema.exception;

import org.springframework.http.HttpStatus;

public class DatabaseUnavailableException extends ApiException {
    public DatabaseUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "Tạm thời không thể truy cập cơ sở dữ liệu.");
    }
}
