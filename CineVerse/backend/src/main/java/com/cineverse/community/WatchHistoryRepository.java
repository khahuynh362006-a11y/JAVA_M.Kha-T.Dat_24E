package com.cineverse.community;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {
    Optional<WatchHistory> findByUserIdAndMovieId(Long userId, Long movieId);

    List<WatchHistory> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
