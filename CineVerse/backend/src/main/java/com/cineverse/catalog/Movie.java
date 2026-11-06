package com.cineverse.catalog;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "movies")
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String title;
    public String originalTitle;

    @Column(unique = true)
    public String slug;

    @Column(columnDefinition = "TEXT")
    public String synopsis;

    public String shortDescription;
    public String type;
    public Long countryId;
    public int releaseYear;
    public int duration;
    public String ageRating;
    public String language;
    public String director;
    public String castNames;
    public String tags;
    public String poster;
    public String backdrop;
    public String videoFile;
    public String trailerFile;
    public String subtitleFile;
    public String status = "DRAFT";
    public boolean vip;
    public boolean featured;
    public int priority;
    public boolean deleted;

    @Column(columnDefinition = "TEXT")
    public String searchText;

    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
    @Version public long version;

    @org.hibernate.annotations.BatchSize(size = 48)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "movie_genres",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"))
    public Set<Genre> genres = new LinkedHashSet<>();

    @org.hibernate.annotations.Formula(
            "(select coalesce(avg(r.stars),0) from reviews r where r.movie_id=id and"
                    + " r.hidden=false)")
    public double rating;

    @org.hibernate.annotations.Formula("(select count(*) from movie_views v where v.movie_id=id)")
    public long views;
}
