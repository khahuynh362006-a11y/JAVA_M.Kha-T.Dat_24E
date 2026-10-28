package com.khadat.cinema.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Scalar FK columns own writes; associations are lazy read-only views. */
@Entity
@Table(name = "movie_genres")
public class MovieGenre {
    @EmbeddedId
    private MovieGenreId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Genre genre;

    public MovieGenre() { }

    public MovieGenreId getId() { return id; }
    public void setId(MovieGenreId id) { this.id = id; }
    public Movie getMovie() { return movie; }
    public Genre getGenre() { return genre; }
}
