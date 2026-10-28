package com.khadat.cinema.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class MovieGenreId implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "movie_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long movieId;
    @Column(name = "genre_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long genreId;
    public MovieGenreId() { }
    public MovieGenreId(Long movieId, Long genreId) {
        this.movieId = movieId;
        this.genreId = genreId;
    }
    public Long getMovieId() { return movieId; }
    public Long getGenreId() { return genreId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MovieGenreId that)) return false;
        return Objects.equals(movieId, that.movieId) && Objects.equals(genreId, that.genreId);
    }
    @Override public int hashCode() { return Objects.hash(movieId, genreId); }
}
