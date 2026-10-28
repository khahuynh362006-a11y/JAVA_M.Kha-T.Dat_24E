package com.khadat.cinema.repository;
import com.khadat.cinema.entity.BookingSeat;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface BookingSeatRepository extends JpaRepository<BookingSeat,Long> {
    List<BookingSeat> findByBookingIdInOrderByBookingIdAscSeatIdAsc(List<Long> bookingIds);
    @Query("select s.seatId from BookingSeat s where s.showtimeId=:id and s.bookingStatus='CONFIRMED'")
    List<Long> occupied(@Param("id") Long id);
}
