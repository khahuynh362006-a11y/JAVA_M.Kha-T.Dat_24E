package com.cineverse.booking;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class BookingDtos {
    private BookingDtos() {}

    public record CinemaView(Long id, String name, String city, String address, boolean active) {}

    public record ShowtimeView(
            Long id,
            Long movieId,
            String movieTitle,
            String movieSlug,
            String poster,
            int duration,
            String ageRating,
            Long auditoriumId,
            String auditoriumName,
            String format,
            Long cinemaId,
            String cinemaName,
            String city,
            String address,
            Instant startsAt,
            Instant endsAt,
            long basePrice,
            long availableSeats,
            long totalSeats) {}

    public record SeatView(
            Long id, String rowLabel, int number, String kind, long price, String status) {}

    public record SeatMap(ShowtimeView showtime, List<SeatView> seats, Instant serverTime) {}

    public record HoldInput(
            @NotNull Long showtimeId, @NotEmpty @Size(max = 8) List<@NotNull Long> seatIds) {}

    public record ItemView(Long seatId, String seatLabel, String seatKind, long price) {}

    public record TicketView(
            String id,
            Long seatId,
            String seatLabel,
            String code,
            String status,
            Instant issuedAt,
            Instant usedAt) {}

    public record BookingView(
            String id,
            Long userId,
            String status,
            long totalAmount,
            Instant holdUntil,
            Instant createdAt,
            Instant confirmedAt,
            ShowtimeView showtime,
            List<ItemView> items,
            List<TicketView> tickets,
            String paymentOrderId) {}
}
