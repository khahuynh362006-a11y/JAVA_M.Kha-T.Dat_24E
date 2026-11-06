package com.cineverse.catalog;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.*;

public final class CatalogDtos {
    public record Lookup(Long id, String name, String slug, boolean active) {}

    public record MovieView(
            Long id,
            String title,
            String originalTitle,
            String slug,
            String synopsis,
            String shortDescription,
            String type,
            Long countryId,
            int releaseYear,
            int duration,
            String ageRating,
            String language,
            String director,
            String castNames,
            String tags,
            String poster,
            String backdrop,
            String status,
            boolean vip,
            boolean featured,
            int priority,
            double rating,
            long views,
            List<Lookup> genres,
            Instant createdAt) {}

    public record MovieInput(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 200) String originalTitle,
            @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max = 220) String slug,
            @NotBlank @Size(max = 12000) String synopsis,
            @NotBlank @Size(max = 600) String shortDescription,
            @Pattern(regexp = "MOVIE|SERIES") @NotNull String type,
            @NotNull Long countryId,
            @Min(1888) @Max(2100) int releaseYear,
            @Min(1) @Max(1000) int duration,
            @NotBlank @Size(max = 10) String ageRating,
            @NotBlank @Size(max = 80) String language,
            @NotBlank @Size(max = 160) String director,
            @NotBlank @Size(max = 600) String castNames,
            @Size(max = 400) String tags,
            @NotBlank @Size(max = 500) String poster,
            @NotBlank @Size(max = 500) String backdrop,
            @Pattern(regexp = "DRAFT|PUBLISHED|COMPLETED") @NotNull String status,
            boolean vip,
            boolean featured,
            @Min(0) @Max(100) int priority,
            @NotEmpty @Size(max = 15) List<Long> genreIds,
            @Size(max = 200) String videoFile,
            @Size(max = 200) String trailerFile,
            @Size(max = 200) String subtitleFile) {}

    public record Paged<T>(
            List<T> content, long totalElements, int totalPages, int page, int size) {}

    public record EpisodeView(
            Long id,
            int number,
            String title,
            String description,
            int duration,
            boolean published,
            boolean vip) {}

    public record SeasonView(Long id, int number, String title, List<EpisodeView> episodes) {}

    public record Detail(MovieView movie, List<SeasonView> seasons, List<MovieView> related) {}

    public static Lookup lookup(Genre g) {
        return new Lookup(g.id, g.name, g.slug, g.active);
    }

    public static Lookup lookup(Country c) {
        return new Lookup(c.id, c.name, c.slug, c.active);
    }

    public static MovieView view(Movie m) {
        return new MovieView(
                m.id,
                m.title,
                m.originalTitle,
                m.slug,
                m.synopsis,
                m.shortDescription,
                m.type,
                m.countryId,
                m.releaseYear,
                m.duration,
                m.ageRating,
                m.language,
                m.director,
                m.castNames,
                m.tags,
                m.poster,
                m.backdrop,
                m.status,
                m.vip,
                m.featured,
                m.priority,
                m.rating,
                m.views,
                m.genres.stream().map(CatalogDtos::lookup).toList(),
                m.createdAt);
    }
}
