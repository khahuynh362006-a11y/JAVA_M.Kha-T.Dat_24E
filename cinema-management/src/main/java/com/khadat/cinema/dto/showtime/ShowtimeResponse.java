package com.khadat.cinema.dto.showtime;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record ShowtimeResponse(Long id,Long movieId,String movieTitle,String posterUrl,Long roomId,String roomCode,String roomName,
    OffsetDateTime startsAt,OffsetDateTime endsAt,Integer durationMinutes,Integer minimumAge,
    BigDecimal standardPrice,BigDecimal vipPrice,Long priceVersion,String status,String phase) { }
