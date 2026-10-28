package com.khadat.cinema.mapper;

import com.khadat.cinema.dto.genre.GenreRequest;
import com.khadat.cinema.dto.genre.GenreResponse;
import com.khadat.cinema.entity.Genre;

public final class GenreMapper {
    private GenreMapper() { }
    public static GenreResponse response(Genre genre) {
        return new GenreResponse(genre.getId(), genre.getName(), genre.getDescription());
    }
    public static void apply(GenreRequest request, Genre genre) {
        genre.setName(request.name().strip());
        genre.setDescription(request.description() == null || request.description().isBlank()
            ? null : request.description().strip());
    }
}
