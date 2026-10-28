package com.khadat.cinema.dto.statistics;
import java.math.BigDecimal;
import java.time.*;
public final class StatisticsRows {
    private StatisticsRows() { }
    public record ShowtimesDay(LocalDate date,long totalShowtimes,long scheduledShowtimes,long cancelledShowtimes) { }
    public record BookedSeatsMovie(Long movieId,String movieTitle,long activeBookings,long bookedSeats,BigDecimal bookingValue) { }
    public record CancellationDay(LocalDate date,long cancelledBookings,long cancelledSeats,BigDecimal cancelledBookingValue) { }
    public record OccupancyRaw(Long showtimeId,Long movieId,String movieTitle,Long roomId,String roomCode,LocalDateTime startsAt,long bookedSeats,Integer snapshot,long currentActiveSeats) { }
    public record Occupancy(Long showtimeId,Long movieId,String movieTitle,Long roomId,String roomCode,OffsetDateTime startsAt,
        long bookedSeats,Long capacity,String capacityBasis,BigDecimal occupancyPercent,boolean anomaly) { }
}
