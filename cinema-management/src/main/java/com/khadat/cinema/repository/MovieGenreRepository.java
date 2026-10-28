package com.khadat.cinema.repository;

import com.khadat.cinema.entity.MovieGenre;
import com.khadat.cinema.entity.MovieGenreId;
import com.khadat.cinema.dto.movie.MovieGenreView;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MovieGenreRepository extends JpaRepository<MovieGenre, MovieGenreId> {
    boolean existsByIdGenreId(Long genreId);
    @Modifying(flushAutomatically=true)
    @Query("delete from MovieGenre link where link.id.movieId = :movieId")
    void deleteForMovie(@Param("movieId") Long movieId);
    @Query("select new com.khadat.cinema.dto.movie.MovieGenreView(link.id.movieId, g.id, g.name, g.description) from MovieGenre link join link.genre g where link.id.movieId in :ids order by link.id.movieId, link.id.genreId")
    List<MovieGenreView> findForMovies(@Param("ids") List<Long> ids);
}
