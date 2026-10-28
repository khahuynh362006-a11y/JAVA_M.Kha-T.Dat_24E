package com.khadat.cinema.repository;

import com.khadat.cinema.entity.Movie;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Movie m where m.id = :id")
    Optional<Movie> findLocked(@Param("id") Long id);
    @Query(value="select count(*) from showtimes where movie_id = :id", nativeQuery=true)
    long countShowtimes(@Param("id") Long id);
    @Query(value="select count(*) from showtimes where movie_id = :id and status = 'SCHEDULED' and ends_at > :now",nativeQuery=true)
    long countUnfinishedShowtimes(@Param("id") Long id, @Param("now") LocalDateTime now);
}
