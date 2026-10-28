package com.khadat.cinema.repository;

import com.khadat.cinema.entity.UserRole;
import com.khadat.cinema.entity.UserRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    @Query("select link.role.code from UserRole link where link.id.userId = :userId order by link.role.code")
    List<String> findRoleCodes(@Param("userId") Long userId);
}
