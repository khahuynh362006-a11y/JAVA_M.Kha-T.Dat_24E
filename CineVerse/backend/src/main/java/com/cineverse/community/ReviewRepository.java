package com.cineverse.community;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByUserIdAndMovieId(Long userId, Long movieId);

    Page<Review> findByMovieIdAndHiddenFalseOrderByCreatedAtDesc(Long movieId, Pageable pageable);
}
