package com.khadat.cinema.dto.movie;

import com.khadat.cinema.dto.genre.GenreResponse;
import java.time.LocalDateTime;
import java.util.List;

public record MovieResponse(Long id, String code, String title, String description, Integer durationMinutes,
    Integer minimumAge, String posterUrl, String trailerUrl, String status,
    List<Long> genreIds, List<GenreResponse> genres, LocalDateTime createdAt, LocalDateTime updatedAt) { }
