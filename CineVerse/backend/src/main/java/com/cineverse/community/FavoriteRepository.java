package com.cineverse.community;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    Optional<Favorite> findByUserIdAndMovieId(Long userId, Long movieId);

    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);
}
