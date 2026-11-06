package com.cineverse.catalog;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface GenreRepository extends JpaRepository<Genre, Long> {}
