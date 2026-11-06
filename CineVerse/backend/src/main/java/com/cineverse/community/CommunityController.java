package com.cineverse.community;

import static com.cineverse.community.CommunityService.*;

import com.cineverse.catalog.CatalogDtos;
import com.cineverse.security.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class CommunityController {
    private final CommunityService service;
    private final CurrentUser current;
    private final JdbcTemplate db;

    public CommunityController(CommunityService service, CurrentUser current, JdbcTemplate db) {
        this.service = service;
        this.current = current;
        this.db = db;
    }

    @GetMapping({"/favorites", "/users/me/favorites"})
    public List<CatalogDtos.MovieView> favorites() {
        return service.favorites(current.id());
    }

    @PostMapping("/favorites")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favorite(@Valid @RequestBody FavoriteInput in) {
        service.favorite(current.id(), in.movieId());
    }

    @DeleteMapping("/favorites/{movieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(@PathVariable Long movieId) {
        service.unfavorite(current.id(), movieId);
    }

    @GetMapping({"/watch-history", "/users/me/watch-history"})
    public List<HistoryView> history() {
        return service.history(current.id());
    }

    @PutMapping("/watch-history/progress")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void progress(@Valid @RequestBody Progress in) {
        service.progress(current.id(), in);
    }

    @DeleteMapping("/watch-history/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        service.removeHistory(current.id(), id);
    }

    @DeleteMapping("/watch-history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() {
        service.clearHistory(current.id());
    }

    @GetMapping("/movies/{movieId}/reviews")
    public CatalogDtos.Paged<ReviewView> reviews(
            @PathVariable Long movieId, @RequestParam(defaultValue = "0") int page) {
        return service.reviews(movieId, page);
    }

    @PostMapping("/movies/{movieId}/reviews")
    public ReviewView review(@PathVariable Long movieId, @Valid @RequestBody ReviewInput in) {
        return service.review(current.id(), movieId, in);
    }

    @PatchMapping("/reviews/{id}")
    public ReviewView edit(@PathVariable Long id, @Valid @RequestBody ReviewInput in) {
        return service.editReview(current.id(), id, in);
    }

    @DeleteMapping("/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteReview(current.id(), id);
    }

    @PostMapping("/reviews/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@PathVariable Long id, @Valid @RequestBody Report in) {
        service.report(current.id(), id, in.reason());
    }

    @GetMapping({"/notifications", "/users/me/notifications"})
    public List<Map<String, Object>> notifications() {
        return db.queryForList(
                "select * from notifications where user_id=? order by created_at desc limit 100",
                current.id());
    }

    @PatchMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id) {
        db.update(
                "update notifications set read_at=? where id=? and user_id=?",
                Timestamp.from(Instant.now()),
                id,
                current.id());
    }

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll() {
        db.update(
                "update notifications set read_at=? where user_id=? and read_at is null",
                Timestamp.from(Instant.now()),
                current.id());
    }
}
