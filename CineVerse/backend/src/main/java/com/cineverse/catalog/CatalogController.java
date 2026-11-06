package com.cineverse.catalog;

import static com.cineverse.catalog.CatalogDtos.*;

import com.cineverse.security.CurrentUser;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalog;
    private final JdbcTemplate db;
    private final CurrentUser current;

    public CatalogController(CatalogService catalog, JdbcTemplate db, CurrentUser current) {
        this.catalog = catalog;
        this.db = db;
        this.current = current;
    }

    @GetMapping("/movies")
    public Paged<MovieView> movies(@RequestParam Map<String, String> q) {
        return catalog.list(q, false);
    }

    @GetMapping("/movies/search")
    public Paged<MovieView> search(@RequestParam Map<String, String> q) {
        return catalog.list(q, false);
    }

    @GetMapping("/movies/featured")
    public Paged<MovieView> featured() {
        return catalog.featured();
    }

    @GetMapping("/movies/trending")
    public Paged<MovieView> trending() {
        return catalog.list(Map.of("sort", "popular", "size", "12"), false);
    }

    @GetMapping("/movies/new")
    public Paged<MovieView> fresh() {
        return catalog.list(Map.of("sort", "new", "size", "12"), false);
    }

    @GetMapping("/movies/top-rated")
    public Paged<MovieView> rated() {
        return catalog.list(Map.of("sort", "rating", "size", "10"), false);
    }

    @GetMapping("/movies/{slug}")
    public Detail detail(@PathVariable String slug) {
        return catalog.detail(slug);
    }

    @GetMapping("/movies/{id}/related")
    public List<MovieView> related(@PathVariable Long id) {
        return catalog.related(id);
    }

    @GetMapping("/users/me/recommendations")
    public List<MovieView> recommend() {
        return catalog.recommend(current.id());
    }

    @GetMapping("/genres")
    public List<Lookup> genres() {
        return catalog.genres();
    }

    @GetMapping("/countries")
    public List<Lookup> countries() {
        return catalog.countries();
    }

    @GetMapping("/banners")
    public List<Map<String, Object>> banners() {
        return db.queryForList(
                "select b.id,b.movie_id,b.eyebrow,b.sort_order,m.slug from banners b join movies m"
                    + " on b.movie_id=m.id where b.active=true and m.deleted=false and m.status in"
                    + " ('PUBLISHED','COMPLETED') order by b.sort_order");
    }

    @GetMapping("/settings")
    public Map<String, String> settings() {
        Map<String, String> out = new LinkedHashMap<>();
        db.query(
                "select setting_key,setting_value from site_settings",
                rs -> {
                    out.put(rs.getString(1), rs.getString(2));
                });
        return out;
    }
}
