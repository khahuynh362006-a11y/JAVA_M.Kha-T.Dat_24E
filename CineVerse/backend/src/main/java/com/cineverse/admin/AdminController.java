package com.cineverse.admin;

import static com.cineverse.admin.AdminService.*;

import com.cineverse.catalog.*;
import com.cineverse.security.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService admin;
    private final CatalogService catalog;
    private final CurrentUser current;

    public AdminController(AdminService admin, CatalogService catalog, CurrentUser current) {
        this.admin = admin;
        this.catalog = catalog;
        this.current = current;
    }

    @GetMapping({"/dashboard", "/analytics"})
    public Map<String, Object> dashboard(@RequestParam(defaultValue = "30") int days) {
        return admin.dashboard(days);
    }

    @GetMapping({"/movies", "/series"})
    public CatalogDtos.Paged<CatalogDtos.MovieView> movies(@RequestParam Map<String, String> q) {
        return catalog.list(q, true);
    }

    @GetMapping("/movies/{id}")
    public CatalogDtos.MovieInput movie(@PathVariable Long id) {
        return catalog.edit(id);
    }

    @PostMapping({"/movies", "/series"})
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.MovieView create(@Valid @RequestBody CatalogDtos.MovieInput in) {
        return catalog.save(null, in, current.id());
    }

    @PutMapping({"/movies/{id}", "/series/{id}"})
    public CatalogDtos.MovieView update(
            @PathVariable Long id, @Valid @RequestBody CatalogDtos.MovieInput in) {
        return catalog.save(id, in, current.id());
    }

    @DeleteMapping({"/movies/{id}", "/series/{id}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMovie(@PathVariable Long id) {
        catalog.delete(id, current.id());
    }

    @GetMapping("/{resource}")
    public CatalogDtos.Paged<Map<String, Object>> list(
            @PathVariable String resource, @RequestParam Map<String, String> q) {
        return admin.list(resource, q);
    }

    @PostMapping({"/genres", "/countries"})
    public Map<String, Long> createLookup(
            jakarta.servlet.http.HttpServletRequest r, @Valid @RequestBody LookupInput in) {
        return Map.of("id", admin.lookup(last(r), null, in, current.id()));
    }

    @PutMapping({"/genres/{id}", "/countries/{id}"})
    public Map<String, Long> updateLookup(
            jakarta.servlet.http.HttpServletRequest r,
            @PathVariable Long id,
            @Valid @RequestBody LookupInput in) {
        return Map.of(
                "id",
                admin.lookup(
                        r.getRequestURI().contains("/genres/") ? "genres" : "countries",
                        id,
                        in,
                        current.id()));
    }

    @PostMapping("/seasons")
    public Map<String, Long> season(@Valid @RequestBody SeasonInput in) {
        return Map.of("id", admin.season(null, in, current.id()));
    }

    @PutMapping("/seasons/{id}")
    public Map<String, Long> season(@PathVariable Long id, @Valid @RequestBody SeasonInput in) {
        return Map.of("id", admin.season(id, in, current.id()));
    }

    @PostMapping("/episodes")
    public Map<String, Long> episode(@Valid @RequestBody EpisodeInput in) {
        return Map.of("id", admin.episode(null, in, current.id()));
    }

    @PutMapping("/episodes/{id}")
    public Map<String, Long> episode(@PathVariable Long id, @Valid @RequestBody EpisodeInput in) {
        return Map.of("id", admin.episode(id, in, current.id()));
    }

    @PostMapping("/subscription-plans")
    public Map<String, Long> plan(@Valid @RequestBody PlanInput in) {
        return Map.of("id", admin.plan(null, in, current.id()));
    }

    @PutMapping("/subscription-plans/{id}")
    public Map<String, Long> plan(@PathVariable Long id, @Valid @RequestBody PlanInput in) {
        return Map.of("id", admin.plan(id, in, current.id()));
    }

    @PostMapping("/banners")
    public Map<String, Long> banner(@Valid @RequestBody BannerInput in) {
        return Map.of("id", admin.banner(null, in, current.id()));
    }

    @PutMapping("/banners/{id}")
    public Map<String, Long> banner(@PathVariable Long id, @Valid @RequestBody BannerInput in) {
        return Map.of("id", admin.banner(id, in, current.id()));
    }

    @DeleteMapping("/{resource}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String resource, @PathVariable Long id) {
        admin.delete(resource, id, current.id());
    }

    @PatchMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void user(@PathVariable Long id, @Valid @RequestBody UserAction in) {
        admin.user(id, in, current.id());
    }

    @PatchMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moderate(@PathVariable Long id, @Valid @RequestBody Moderation in) {
        admin.moderate(id, in, current.id());
    }

    @PostMapping("/notifications")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void announce(@Valid @RequestBody Announcement in) {
        admin.announce(in, current.id());
    }

    @PostMapping("/email-logs/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@PathVariable Long id) {
        admin.retryEmail(id, current.id());
    }

    @PatchMapping("/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void settings(@RequestBody Map<String, String> in) {
        admin.settings(in, current.id());
    }

    @GetMapping("/payments/{id}")
    public Map<String, Object> payment(@PathVariable String id) {
        return admin.payment(id);
    }

    @GetMapping("/payments/export")
    public ResponseEntity<String> export() {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=cineverse-payments.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(admin.exportPayments());
    }

    private String last(jakarta.servlet.http.HttpServletRequest r) {
        String uri = r.getRequestURI();
        return uri.substring(uri.lastIndexOf('/') + 1);
    }
}
