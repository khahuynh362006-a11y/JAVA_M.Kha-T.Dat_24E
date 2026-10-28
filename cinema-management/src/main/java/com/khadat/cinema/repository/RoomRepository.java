package com.khadat.cinema.repository;
import com.khadat.cinema.entity.Room;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface RoomRepository extends JpaRepository<Room,Long>,JpaSpecificationExecutor<Room> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code,Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from Room r where r.id=:id")
    Optional<Room> findLocked(@Param("id") Long id);
    @Query(value="select count(*) from showtimes where room_id=:id",nativeQuery=true)
    long countShowtimes(@Param("id") Long id);
    @Query(value="select count(*) from showtimes where room_id=:id and status='SCHEDULED' and ends_at>:now",nativeQuery=true)
    long countUnfinished(@Param("id") Long id,@Param("now") LocalDateTime now);
}
