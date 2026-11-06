package com.cineverse.catalog;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface SeasonRepository extends JpaRepository<Season, Long> {
    List<Season> findByMovieIdOrderByNumber(Long movieId);
}
