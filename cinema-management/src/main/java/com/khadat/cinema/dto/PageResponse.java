package com.khadat.cinema.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, long total, int page, int size, int totalPages) {
    public static <T> PageResponse<T> from(Page<?> page, List<T> items) {
        return new PageResponse<>(List.copyOf(items), page.getTotalElements(), page.getNumber(),
            page.getSize(), page.getTotalPages());
    }
}
