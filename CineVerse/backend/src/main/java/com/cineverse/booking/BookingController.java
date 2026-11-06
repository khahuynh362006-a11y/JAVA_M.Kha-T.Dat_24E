package com.cineverse.booking;

import static com.cineverse.booking.BookingDtos.*;

import com.cineverse.security.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class BookingController {
    private final BookingService bookings;
    private final CurrentUser current;

    public BookingController(BookingService bookings, CurrentUser current) {
        this.bookings = bookings;
        this.current = current;
    }

    @GetMapping("/cinemas")
    public List<CinemaView> cinemas() {
        return bookings.cinemas();
    }

    @GetMapping("/showtimes")
    public List<ShowtimeView> showtimes(
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) LocalDate date) {
        return bookings.showtimes(movieId, cinemaId, date);
    }

    @GetMapping("/showtimes/{id}")
    public ShowtimeView showtime(@PathVariable Long id) {
        return bookings.showtime(id);
    }

    @GetMapping("/showtimes/{id}/seats")
    public SeatMap seats(@PathVariable Long id) {
        return bookings.seats(id);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingView hold(@Valid @RequestBody HoldInput in) {
        return bookings.hold(current.id(), in);
    }

    @GetMapping({"/bookings/me", "/tickets/me"})
    public List<BookingView> mine() {
        return bookings.mine(current.id());
    }

    @GetMapping("/bookings/{id}")
    public BookingView booking(@PathVariable String id) {
        var u = current.get();
        return bookings.get(u.id, id, u.role.equals("ADMIN"));
    }

    @PostMapping("/bookings/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String id) {
        bookings.cancel(current.id(), id);
    }

    public record CheckIn(@jakarta.validation.constraints.NotBlank String code) {}

    @PostMapping("/admin/tickets/check-in")
    public TicketView checkIn(@Valid @RequestBody CheckIn in) {
        String code = in.code().replace("CINEVERSE:TICKET:", "").strip();
        return bookings.checkIn(code, current.id());
    }
}
