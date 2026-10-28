package com.khadat.cinema.dto.booking;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
public record BookingResponse(Long id,String code,Long showtimeId,Long roomId,String movieTitle,String roomCode,
    OffsetDateTime startsAt,OffsetDateTime endsAt,Long createdBy,String createdByName,OffsetDateTime createdAt,
    Long priceVersion,Boolean ageChecked,BigDecimal totalAmount,String status,OffsetDateTime cancelledAt,Long cancelledBy,
    String cancelReason,List<BookedSeatResponse> seats) { }
