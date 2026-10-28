package com.khadat.cinema.repository;

import com.khadat.cinema.entity.Genre;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GenreRepository extends JpaRepository<Genre, Long>, JpaSpecificationExecutor<Genre> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Genre g where g.id = :id")
    Optional<Genre> findLocked(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Genre g where g.id in :ids order by g.id")
    List<Genre> findAllLocked(@Param("ids") List<Long> ids);
}
