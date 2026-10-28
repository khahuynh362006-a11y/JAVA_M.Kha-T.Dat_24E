package com.khadat.cinema.mapper;
import com.khadat.cinema.dto.showtime.*;
import java.time.*;
public final class ShowtimeMapper {
    private ShowtimeMapper() { }
    public static final ZoneOffset OFFSET=ZoneOffset.ofHours(7);
    public static ShowtimeResponse response(ShowtimeView v,Clock clock) {
        LocalDateTime now=LocalDateTime.now(clock);
        String phase=v.status().equals("CANCELLED")?"CANCELLED":!now.isBefore(v.endsAt())?"COMPLETED":!now.isBefore(v.startsAt())?"IN_PROGRESS":"UPCOMING";
        return new ShowtimeResponse(v.id(),v.movieId(),v.movieTitle(),v.posterUrl(),v.roomId(),v.roomCode(),v.roomName(),
            v.startsAt().atOffset(OFFSET),v.endsAt().atOffset(OFFSET),v.durationMinutes(),v.minimumAge(),v.standardPrice(),v.vipPrice(),v.priceVersion(),v.status(),phase);
    }
}
