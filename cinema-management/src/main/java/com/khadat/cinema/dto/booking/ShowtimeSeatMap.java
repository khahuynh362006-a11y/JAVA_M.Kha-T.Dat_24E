package com.khadat.cinema.dto.booking;
import com.khadat.cinema.dto.showtime.ShowtimeResponse;
import java.math.BigDecimal;
import java.util.List;
public record ShowtimeSeatMap(ShowtimeResponse showtime,boolean bookable,List<Seat> seats) {
    public record Seat(Long id,String rowLabel,Integer seatNumber,String code,String seatType,String physicalStatus,
        String availability,BigDecimal price) { }
}
