package com.khadat.cinema.service;
import com.khadat.cinema.config.CinemaPrincipal;
import com.khadat.cinema.dto.PageResponse;
import com.khadat.cinema.dto.booking.*;
import com.khadat.cinema.entity.*;
import com.khadat.cinema.exception.*;
import com.khadat.cinema.mapper.*;
import com.khadat.cinema.repository.*;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class BookingService {
    private final BookingRepository bookings;private final BookingSeatRepository details;private final ShowtimeRepository shows;private final SeatRepository seats;
    private final MovieRepository movies;private final RoomRepository rooms;private final BookingTransactionService transactions;private final Clock clock;
    public BookingService(BookingRepository bookings,BookingSeatRepository details,ShowtimeRepository shows,SeatRepository seats,MovieRepository movies,RoomRepository rooms,BookingTransactionService transactions,Clock clock) {
        this.bookings=bookings;this.details=details;this.shows=shows;this.seats=seats;this.movies=movies;this.rooms=rooms;this.transactions=transactions;this.clock=clock;
    }
    public BookingCreation create(BookingRequest request,String header,CinemaPrincipal actor) {
        if(header==null||!header.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))throw CatalogQuery.invalid("idempotencyKey","Idempotency-Key phải là UUID đầy đủ.");
        if(new HashSet<>(request.seatIds()).size()!=request.seatIds().size())throw CatalogQuery.invalid("seatIds","Không được gửi ID ghế trùng.");
        String key=UUID.fromString(header).toString();String canonical=request.showtimeId()+"|"+request.seatIds().stream().sorted().toList()+"|"+request.priceVersion()+"|"+request.ageChecked();
        String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 unavailable");}
        // Catch only AFTER the transactional proxy has rolled back; never replay in a failed transaction.
        try{return transactions.create(request,key,hash,actor);}
        catch(DataIntegrityViolationException conflict) {
            var existing=bookings.findByCreatedByAndIdempotencyKey(actor.getId(),key);
            if(existing.isPresent())return BookingTransactionService.replay(existing.get(),hash);
            throw new BusinessException("BOOKING_CONFLICT","Ghế hoặc dữ liệu đặt chỗ vừa xung đột; hãy tải lại sơ đồ.");
        }
    }
    @Transactional(readOnly=true)
    public BookingResponse detail(Long id){CatalogQuery.positiveId(id,"id");return responses(List.of(bookings.findById(id).orElseThrow(ResourceNotFoundException::new))).getFirst();}
    @Transactional(readOnly=true)
    public BookingResponse byCode(String code){if(code.length()>40)throw CatalogQuery.invalid("code","Mã tối đa 40 ký tự.");return responses(List.of(bookings.findByCode(code.strip()).orElseThrow(ResourceNotFoundException::new))).getFirst();}
    @Transactional(readOnly=true)
    public PageResponse<BookingResponse> list(String q,Long showtimeId,String status,int page,int size,String sort,String direction) {
        String term=CatalogQuery.term(q);if(showtimeId!=null)CatalogQuery.positiveId(showtimeId,"showtimeId");
        if(status!=null&&!Set.of("CONFIRMED","CANCELLED").contains(status))throw CatalogQuery.invalid("status","Trạng thái phải CONFIRMED hoặc CANCELLED.");
        var result=bookings.findAll((root,query,cb)->{List<Predicate> p=new ArrayList<>();
            if(!term.isEmpty())p.add(cb.like(root.get("code"),CatalogQuery.contains(term),'!'));
            if(showtimeId!=null)p.add(cb.equal(root.get("showtimeId"),showtimeId));if(status!=null)p.add(cb.equal(root.get("status"),status));return cb.and(p.toArray(Predicate[]::new));
        },CatalogQuery.page(page,size,sort,direction,Set.of("id","code","createdAt","totalAmount","status")));
        return PageResponse.from(result,responses(result.getContent()));
    }
    private List<BookingResponse> responses(List<Booking> list) {
        if(list.isEmpty())return List.of();Map<Long,Showtime> showMap=new HashMap<>();shows.findAllById(list.stream().map(Booking::getShowtimeId).distinct().toList()).forEach(s->showMap.put(s.getId(),s));
        Map<Long,List<BookingSeat>> children=new HashMap<>();details.findByBookingIdInOrderByBookingIdAscSeatIdAsc(list.stream().map(Booking::getId).toList()).forEach(d->children.computeIfAbsent(d.getBookingId(),key->new ArrayList<>()).add(d));
        return list.stream().map(b->BookingMapper.response(b,showMap.get(b.getShowtimeId()),children.getOrDefault(b.getId(),List.of()))).toList();
    }
    public Long cancel(Long id,CancelBookingRequest request,Long actor){CatalogQuery.positiveId(id,"id");return transactions.cancel(id,request,actor);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ShowtimeSeatMap map(Long showtimeId) {
        CatalogQuery.positiveId(showtimeId,"showtimeId");var v=shows.views(List.of(showtimeId)).stream().findFirst().orElseThrow(ResourceNotFoundException::new);
        Set<Long> occupied=new HashSet<>(details.occupied(showtimeId));
        boolean open=v.status().equals("SCHEDULED")&&v.startsAt().isAfter(LocalDateTime.now(clock))
            &&movies.findById(v.movieId()).orElseThrow().getStatus().equals("ACTIVE")&&rooms.findById(v.roomId()).orElseThrow().getStatus().equals("ACTIVE");
        var physical=seats.findByRoomId(v.roomId()).stream().sorted(Comparator.comparingInt((Seat s)->s.getRowLabel().length()).thenComparing(Seat::getRowLabel).thenComparing(Seat::getSeatNumber)).toList();
        boolean bookable=open&&physical.stream().anyMatch(s->s.getStatus().equals("ACTIVE"));
        var result=physical.stream().map(s->new ShowtimeSeatMap.Seat(s.getId(),s.getRowLabel(),s.getSeatNumber(),s.getCode(),s.getSeatType(),s.getStatus(),
            occupied.contains(s.getId())?"BOOKED":(!s.getStatus().equals("ACTIVE")||!bookable)?"UNAVAILABLE":"AVAILABLE",
            s.getSeatType().equals("VIP")?v.vipPrice():v.standardPrice())).toList();
        return new ShowtimeSeatMap(ShowtimeMapper.response(v,clock),bookable,result);
    }
}
