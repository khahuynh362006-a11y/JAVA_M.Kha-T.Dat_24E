package com.khadat.cinema.repository;
import com.khadat.cinema.entity.Seat;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface SeatRepository extends JpaRepository<Seat,Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id=:id")
    Optional<Seat> findLocked(@Param("id") Long id);
    Optional<Seat> findByIdAndRoomId(Long id,Long roomId);
    List<Seat> findByRoomId(Long roomId);
    long countByRoomId(Long roomId);
    boolean existsByRoomIdAndCodeAndIdNot(Long roomId,String code,Long id);
    boolean existsByRoomIdAndRowLabelAndSeatNumberAndIdNot(Long roomId,String rowLabel,Integer seatNumber,Long id);
    interface Counts { Long getRoomId(); Long getTotal(); Long getActive(); }
    @Query("select s.roomId as roomId,count(s) as total,sum(case when s.status='ACTIVE' then 1 else 0 end) as active from Seat s where s.roomId in :ids group by s.roomId")
    List<Counts> counts(@Param("ids") List<Long> ids);
}
