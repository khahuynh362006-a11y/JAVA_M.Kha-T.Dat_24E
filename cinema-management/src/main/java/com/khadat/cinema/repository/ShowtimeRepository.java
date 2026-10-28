package com.khadat.cinema.repository;
import com.khadat.cinema.entity.Showtime;
import com.khadat.cinema.dto.showtime.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ShowtimeRepository extends JpaRepository<Showtime,Long>,JpaSpecificationExecutor<Showtime> {
    @Query("select new com.khadat.cinema.dto.showtime.ShowtimeContext(s.movieId,s.roomId) from Showtime s where s.id=:id")
    Optional<ShowtimeContext> context(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from Showtime s where s.id=:id")
    Optional<Showtime> findLocked(@Param("id") Long id);
    @Query("select count(s) from Showtime s where s.roomId=:roomId and s.status='SCHEDULED' and s.id<>:excluded and s.startsAt<:upper and s.endsAt>:lower")
    long overlaps(@Param("roomId") Long roomId,@Param("excluded") Long excluded,@Param("lower") LocalDateTime lower,@Param("upper") LocalDateTime upper);
    @Query(value="select count(*) from bookings where showtime_id=:id",nativeQuery=true)
    long bookingCount(@Param("id") Long id);
    @Query("select new com.khadat.cinema.dto.showtime.ShowtimeView(s.id,m.id,m.title,m.posterUrl,r.id,r.code,r.name,s.startsAt,s.endsAt,s.durationMinutesSnapshot,s.minimumAgeSnapshot,s.standardPrice,s.vipPrice,s.priceVersion,s.status,(select count(b) from Booking b where b.showtimeId=s.id)) from Showtime s join s.movie m join s.room r where s.id in :ids")
    List<ShowtimeView> views(@Param("ids") List<Long> ids);
}
