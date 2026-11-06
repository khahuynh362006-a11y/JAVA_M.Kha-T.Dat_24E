package com.cineverse.users;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id=:id")
    Optional<User> lockById(@Param("id") Long id);

    long countByRoleAndLockedFalse(String role);
}
