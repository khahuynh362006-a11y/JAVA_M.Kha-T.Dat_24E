package com.khadat.cinema.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ApiError(OffsetDateTime timestamp, int status, String code,
                       String message, String path, Map<String, List<String>> fieldErrors) {
}
