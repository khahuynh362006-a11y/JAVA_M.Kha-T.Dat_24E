package com.cineverse.auth;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update AuthToken t set t.consumed=true where t.userId=:id and t.kind='REFRESH'")
    int revokeRefresh(@Param("id") Long id);
}
