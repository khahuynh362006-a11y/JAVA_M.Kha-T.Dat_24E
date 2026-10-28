package com.khadat.cinema.service;
import com.khadat.cinema.config.CinemaPrincipal;
import com.khadat.cinema.dto.booking.*;
import com.khadat.cinema.entity.*;
import com.khadat.cinema.exception.*;
import com.khadat.cinema.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class BookingTransactionService {
    private final BookingRepository bookings;private final BookingSeatRepository details;private final ShowtimeRepository shows;
    private final MovieRepository movies;private final RoomRepository rooms;private final SeatRepository seats;private final Clock clock;
    public BookingTransactionService(BookingRepository bookings,BookingSeatRepository details,ShowtimeRepository shows,MovieRepository movies,RoomRepository rooms,SeatRepository seats,Clock clock) {
        this.bookings=bookings;this.details=details;this.shows=shows;this.movies=movies;this.rooms=rooms;this.seats=seats;this.clock=clock;
    }
    private Showtime lockShow(Long id) {
        var c=shows.context(id).orElseThrow(ResourceNotFoundException::new);
        movies.findLocked(c.movieId()).orElseThrow(ResourceNotFoundException::new);
        rooms.findLocked(c.roomId()).orElseThrow(ResourceNotFoundException::new);
        Showtime s=shows.findLocked(id).orElseThrow(ResourceNotFoundException::new);
        if(!s.getMovieId().equals(c.movieId())||!s.getRoomId().equals(c.roomId()))
            throw new BusinessException("CONCURRENT_MODIFICATION","Ngữ cảnh suất vừa đổi; hãy tải lại sơ đồ.");
        return s;
    }
    private void open(Showtime s) {
        if(!s.getStatus().equals("SCHEDULED")||!LocalDateTime.now(clock).isBefore(s.getStartsAt()))
            throw new BusinessException("SHOWTIME_NOT_BOOKABLE","Suất đã hủy hoặc đã bắt đầu, không cho đặt/hủy mới.");
    }
    static BookingCreation replay(Booking existing,String hash) {
        if(!existing.getRequestHash().equals(hash)) throw new BusinessException("IDEMPOTENCY_KEY_REUSED","Khóa yêu cầu đã được dùng với nội dung khác.");
        return new BookingCreation(existing.getId(),true);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public BookingCreation create(BookingRequest request,String key,String hash,CinemaPrincipal actor) {
        var previous=bookings.findByCreatedByAndIdempotencyKey(actor.getId(),key);
        if(previous.isPresent())return replay(previous.get(),hash);
        Showtime s=lockShow(request.showtimeId());
        previous=bookings.findByCreatedByAndIdempotencyKey(actor.getId(),key);
        if(previous.isPresent())return replay(previous.get(),hash);
        open(s);
        Movie movie=movies.findById(s.getMovieId()).orElseThrow(ResourceNotFoundException::new);
        Room room=rooms.findById(s.getRoomId()).orElseThrow(ResourceNotFoundException::new);
        if(!movie.getStatus().equals("ACTIVE")||!room.getStatus().equals("ACTIVE"))
            throw new BusinessException("RESOURCE_INACTIVE","Phim hoặc phòng đã ngừng sử dụng.");
        if(!s.getPriceVersion().equals(request.priceVersion()))throw new BusinessException("PRICE_CHANGED","Giá đã thay đổi; hãy tải lại sơ đồ và xác nhận giá mới.");
        if(s.getMinimumAgeSnapshot()>0&&!request.ageChecked())throw CatalogQuery.invalid("ageChecked","Nhân viên phải xác nhận đã kiểm tra độ tuổi.");
        List<Seat> selected=new ArrayList<>();
        for(Long id:request.seatIds().stream().sorted().toList()) {
            Seat seat=seats.findLocked(id).orElseThrow(()->CatalogQuery.invalid("seatIds","Có ghế không tồn tại."));
            if(!seat.getRoomId().equals(s.getRoomId()))throw CatalogQuery.invalid("seatIds","Ghế không thuộc phòng của suất.");
            if(!seat.getStatus().equals("ACTIVE"))throw new BusinessException("SEAT_UNAVAILABLE","Có ghế đã ngừng sử dụng.");
            selected.add(seat);
        }
        Set<Long> occupied=new HashSet<>(details.occupied(s.getId()));
        if(selected.stream().anyMatch(seat->occupied.contains(seat.getId())))throw new BusinessException("SEAT_ALREADY_BOOKED","Có ghế vừa được người khác đặt; hãy tải lại sơ đồ.");
        BigDecimal total=selected.stream().map(seat->seat.getSeatType().equals("VIP")?s.getVipPrice():s.getStandardPrice()).reduce(BigDecimal.ZERO,BigDecimal::add);
        Booking b=new Booking();b.setCode("BK-"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT));
        b.setShowtimeId(s.getId());b.setRoomId(s.getRoomId());b.setCreatedBy(actor.getId());b.setCreatedByNameSnapshot(actor.getDisplayName());
        b.setMovieTitleSnapshot(movie.getTitle());b.setRoomCodeSnapshot(room.getCode());b.setPriceVersionSnapshot(s.getPriceVersion());
        b.setAgeChecked(request.ageChecked());b.setTotalAmount(total);b.setStatus("CONFIRMED");b.setIdempotencyKey(key);b.setRequestHash(hash);bookings.saveAndFlush(b);
        for(Seat seat:selected) {
            BookingSeat d=new BookingSeat();d.setBookingId(b.getId());d.setShowtimeId(s.getId());d.setRoomId(s.getRoomId());d.setSeatId(seat.getId());
            d.setSeatCodeSnapshot(seat.getCode());d.setSeatTypeSnapshot(seat.getSeatType());d.setUnitPrice(seat.getSeatType().equals("VIP")?s.getVipPrice():s.getStandardPrice());details.save(d);
        }
        details.flush();return new BookingCreation(b.getId(),false);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Long cancel(Long id,CancelBookingRequest request,Long actor) {
        var c=bookings.context(id).orElseThrow(ResourceNotFoundException::new);Showtime s=lockShow(c.showtimeId());
        Booking b=bookings.findLocked(id).orElseThrow(ResourceNotFoundException::new);
        if(b.getStatus().equals("CANCELLED"))return id;
        open(s);b.setStatus("CANCELLED");b.setCancelledAt(LocalDateTime.now(clock));b.setCancelledBy(actor);b.setCancelReason(request.reason().strip());
        bookings.saveAndFlush(b);return id;
    }
}
