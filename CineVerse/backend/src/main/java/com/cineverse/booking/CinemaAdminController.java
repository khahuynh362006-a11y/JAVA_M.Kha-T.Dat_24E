package com.cineverse.booking;

import static com.cineverse.booking.CinemaAdminService.*;

import com.cineverse.security.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class CinemaAdminController {
    private final CinemaAdminService service;
    private final CurrentUser current;
    private final BookingService bookings;

    public CinemaAdminController(
            CinemaAdminService service, CurrentUser current, BookingService bookings) {
        this.service = service;
        this.current = current;
        this.bookings = bookings;
    }

    @PostMapping("/cinemas")
    public Map<String, Long> cinema(@Valid @RequestBody CinemaInput in) {
        return Map.of("id", service.cinema(null, in, current.id()));
    }

    @PutMapping("/cinemas/{id}")
    public Map<String, Long> cinema(@PathVariable Long id, @Valid @RequestBody CinemaInput in) {
        return Map.of("id", service.cinema(id, in, current.id()));
    }

    @PostMapping("/auditoriums")
    public Map<String, Long> room(@Valid @RequestBody RoomInput in) {
        return Map.of("id", service.room(null, in, current.id()));
    }

    @PutMapping("/auditoriums/{id}")
    public Map<String, Long> room(@PathVariable Long id, @Valid @RequestBody RoomInput in) {
        return Map.of("id", service.room(id, in, current.id()));
    }

    @PutMapping("/seats/{id}")
    public void seat(@PathVariable Long id, @Valid @RequestBody SeatInput in) {
        service.seat(id, in, current.id());
    }

    @PostMapping("/showtimes")
    public Map<String, Long> showtime(@Valid @RequestBody ShowtimeInput in) {
        return Map.of("id", service.showtime(null, in, current.id()));
    }

    @PutMapping("/showtimes/{id}")
    public Map<String, Long> showtime(@PathVariable Long id, @Valid @RequestBody ShowtimeInput in) {
        return Map.of("id", service.showtime(id, in, current.id()));
    }

    @GetMapping("/bookings/{id}")
    public BookingDtos.BookingView booking(@PathVariable String id) {
        return bookings.get(current.id(), id, true);
    }

    @DeleteMapping({"/cinemas/{id}", "/auditoriums/{id}", "/showtimes/{id}"})
    public void close(jakarta.servlet.http.HttpServletRequest request, @PathVariable Long id) {
        String[] parts = request.getRequestURI().split("/");
        service.delete(parts[parts.length - 2], id, current.id());
    }
}
