package com.khadat.cinema.dto.showtime;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ShowtimeView(Long id,Long movieId,String movieTitle,String posterUrl,Long roomId,String roomCode,String roomName,
    LocalDateTime startsAt,LocalDateTime endsAt,Integer durationMinutes,Integer minimumAge,
    BigDecimal standardPrice,BigDecimal vipPrice,Long priceVersion,String status,Long bookingCount) { }
