package com.cineverse.catalog;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface MovieRepository
        extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {
    Optional<Movie> findBySlugAndDeletedFalse(String slug);
}
