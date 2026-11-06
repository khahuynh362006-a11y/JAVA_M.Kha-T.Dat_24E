package com.cineverse.catalog;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    List<Episode> findBySeasonIdOrderByNumber(Long seasonId);
}
