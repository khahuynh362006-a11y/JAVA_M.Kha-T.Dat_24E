package com.khadat.cinema.mapper;
import com.khadat.cinema.dto.booking.*;
import com.khadat.cinema.entity.*;
import java.time.*;
import java.util.List;
public final class BookingMapper {
    private BookingMapper() { }
    static OffsetDateTime time(LocalDateTime t) { return t==null?null:t.atOffset(ShowtimeMapper.OFFSET); }
    public static BookingResponse response(Booking b,Showtime s,List<BookingSeat> details) {
        return new BookingResponse(b.getId(),b.getCode(),b.getShowtimeId(),b.getRoomId(),b.getMovieTitleSnapshot(),b.getRoomCodeSnapshot(),
            time(s.getStartsAt()),time(s.getEndsAt()),b.getCreatedBy(),b.getCreatedByNameSnapshot(),time(b.getCreatedAt()),b.getPriceVersionSnapshot(),
            b.getAgeChecked(),b.getTotalAmount(),b.getStatus(),time(b.getCancelledAt()),b.getCancelledBy(),b.getCancelReason(),
            details.stream().map(d->new BookedSeatResponse(d.getSeatId(),d.getSeatCodeSnapshot(),d.getSeatTypeSnapshot(),d.getUnitPrice(),d.getBookingStatus())).toList());
    }
}
