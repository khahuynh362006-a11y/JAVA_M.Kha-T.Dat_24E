package com.khadat.cinema.mapper;

import com.khadat.cinema.dto.movie.*;
import com.khadat.cinema.dto.genre.GenreResponse;
import com.khadat.cinema.entity.Movie;
import java.util.List;
import java.util.Locale;

public final class MovieMapper {
    private MovieMapper() { }
    public static String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    public static void apply(MovieRequest request, Movie movie) {
        movie.setCode(request.code().strip().toUpperCase(Locale.ROOT));
        movie.setTitle(request.title().strip()); movie.setDescription(optional(request.description()));
        movie.setDurationMinutes(request.durationMinutes()); movie.setMinimumAge(request.minimumAge());
        movie.setPosterUrl(optional(request.posterUrl())); movie.setTrailerUrl(optional(request.trailerUrl()));
        movie.setStatus(request.status());
    }
    public static MovieResponse response(Movie movie, List<GenreResponse> genres) {
        return new MovieResponse(movie.getId(),movie.getCode(),movie.getTitle(),movie.getDescription(),
            movie.getDurationMinutes(),movie.getMinimumAge(),movie.getPosterUrl(),movie.getTrailerUrl(),movie.getStatus(),
            genres.stream().map(GenreResponse::id).toList(),List.copyOf(genres),movie.getCreatedAt(),movie.getUpdatedAt());
    }
}
