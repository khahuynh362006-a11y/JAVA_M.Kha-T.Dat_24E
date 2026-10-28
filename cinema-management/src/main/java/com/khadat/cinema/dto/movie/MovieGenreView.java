package com.khadat.cinema.dto.movie;

/** Scalar projection also works immediately after inserting links with read-only associations. */
public record MovieGenreView(Long movieId, Long genreId, String name, String description) { }
