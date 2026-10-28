package com.khadat.cinema.repository;
import com.khadat.cinema.entity.Booking;
import com.khadat.cinema.dto.booking.BookingContext;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface BookingRepository extends JpaRepository<Booking,Long>,JpaSpecificationExecutor<Booking> {
    Optional<Booking> findByCreatedByAndIdempotencyKey(Long createdBy,String idempotencyKey);
    Optional<Booking> findByCode(String code);
    @Query("select new com.khadat.cinema.dto.booking.BookingContext(b.id,b.showtimeId) from Booking b where b.id=:id")
    Optional<BookingContext> context(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from Booking b where b.id=:id")
    Optional<Booking> findLocked(@Param("id") Long id);
}
