package com.khadat.cinema.service;

import com.khadat.cinema.exception.FieldValidationException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/** Request values never become arbitrary SQL identifiers. */
final class CatalogQuery {
    private CatalogQuery() { }
    static FieldValidationException invalid(String field, String message) {
        return new FieldValidationException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field, message);
    }
    static Pageable page(int page, int size, String sort, String direction, Set<String> allowed) {
        if (page < 0) throw invalid("page", "page phải từ 0 trở lên.");
        if (size < 1 || size > 100) throw invalid("size", "size phải từ 1 đến 100.");
        if (!allowed.contains(sort)) throw invalid("sort", "Trường sắp xếp không được hỗ trợ.");
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction))
            throw invalid("direction", "direction phải là asc hoặc desc.");
        Sort order = Sort.by(Sort.Direction.fromString(direction), sort);
        if (!sort.equals("id")) order = order.and(Sort.by("id"));
        return PageRequest.of(page, size, order);
    }
    static String term(String query) {
        if (query == null) return "";
        if (query.length() > 200) throw invalid("q", "Từ khóa không được vượt 200 ký tự.");
        return query.strip();
    }
    static String contains(String value) {
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    static void positiveId(Long id, String field) {
        if (id == null || id <= 0) throw invalid(field, "ID phải là số nguyên dương.");
    }
}
