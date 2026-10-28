package com.khadat.cinema.service;
import com.khadat.cinema.dto.PageResponse;
import com.khadat.cinema.dto.showtime.*;
import com.khadat.cinema.entity.*;
import com.khadat.cinema.exception.*;
import com.khadat.cinema.mapper.ShowtimeMapper;
import com.khadat.cinema.repository.*;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @Transactional(readOnly=true)
public class ShowtimeService {
    private final ShowtimeRepository shows;private final MovieRepository movies;private final RoomRepository rooms;private final SeatRepository seats;private final Clock clock;
    public ShowtimeService(ShowtimeRepository shows,MovieRepository movies,RoomRepository rooms,SeatRepository seats,Clock clock) {
        this.shows=shows;this.movies=movies;this.rooms=rooms;this.seats=seats;this.clock=clock;
    }
    public ShowtimeView detail(Long id) {
        CatalogQuery.positiveId(id,"id");return shows.views(List.of(id)).stream().findFirst().orElseThrow(ResourceNotFoundException::new);
    }
    public PageResponse<ShowtimeResponse> list(LocalDate date,OffsetDateTime from,OffsetDateTime to,Long roomId,Long movieId,String status,
        int page,int size,String sort,String direction) {
        if(roomId!=null) CatalogQuery.positiveId(roomId,"roomId");if(movieId!=null) CatalogQuery.positiveId(movieId,"movieId");
        if(status!=null && !Set.of("SCHEDULED","CANCELLED").contains(status)) throw CatalogQuery.invalid("status","Trạng thái phải là SCHEDULED hoặc CANCELLED.");
        if(date!=null && (from!=null||to!=null)) throw CatalogQuery.invalid("date","Chỉ dùng date hoặc cặp from/to.");
        if((from==null)!=(to==null)) throw CatalogQuery.invalid("from","Phải cung cấp đủ from và to.");
        LocalDateTime lower=null,upper=null;
        if(date!=null) {
            if(date.getYear()<1000||date.getYear()>=9999) throw CatalogQuery.invalid("date","Ngày nằm ngoài miền hỗ trợ.");
            lower=date.atStartOfDay();upper=date.plusDays(1).atStartOfDay();
        } else if(from!=null) {
            lower=time(from,"from",false);upper=time(to,"to",false);
            if(!lower.isBefore(upper)||Duration.between(lower,upper).compareTo(Duration.ofDays(31))>0)
                throw CatalogQuery.invalid("to","Khoảng lọc phải dương và không quá 31 ngày.");
        }
        final LocalDateTime start=lower,end=upper;
        var result=shows.findAll((root,query,cb)->{
            List<Predicate> p=new ArrayList<>();
            if(start!=null) { p.add(cb.greaterThanOrEqualTo(root.get("startsAt"),start));p.add(cb.lessThan(root.get("startsAt"),end)); }
            if(roomId!=null)p.add(cb.equal(root.get("roomId"),roomId));if(movieId!=null)p.add(cb.equal(root.get("movieId"),movieId));
            if(status!=null)p.add(cb.equal(root.get("status"),status));return cb.and(p.toArray(Predicate[]::new));
        },CatalogQuery.page(page,size,sort,direction,Set.of("id","startsAt","endsAt","standardPrice","vipPrice","status")));
        Map<Long,ShowtimeView> views=new HashMap<>();
        if(!result.isEmpty()) shows.views(result.getContent().stream().map(Showtime::getId).toList()).forEach(v->views.put(v.id(),v));
        return PageResponse.from(result,result.getContent().stream().map(s->ShowtimeMapper.response(views.get(s.getId()),clock)).toList());
    }
    private LocalDateTime time(OffsetDateTime value,String field,boolean minute) {
        if(!value.getOffset().equals(ShowtimeMapper.OFFSET)||value.getYear()<1000||value.getYear()>9999)
            throw CatalogQuery.invalid(field,"Thời gian phải có offset +07:00 và năm 1000–9999.");
        if(minute && (value.getSecond()!=0||value.getNano()!=0)) throw CatalogQuery.invalid(field,"Thời gian xếp lịch phải chính xác đến phút.");
        return value.toLocalDateTime();
    }
    private void editable(Showtime show) {
        if(!show.getStatus().equals("SCHEDULED")) throw new BusinessException("SHOWTIME_CANCELLED","Suất đã hủy không được sửa, xóa hoặc khôi phục.");
        if(!LocalDateTime.now(clock).isBefore(show.getStartsAt())) throw new BusinessException("SHOWTIME_STARTED","Suất đã bắt đầu; chỉ được xem.");
        if(shows.bookingCount(show.getId())>0) throw new BusinessException("SHOWTIME_HAS_BOOKINGS","Suất đã có lịch sử đặt chỗ; chưa được sửa, hủy hoặc xóa.");
    }
    private Showtime locked(Long id,Long targetMovie,Long targetRoom) {
        CatalogQuery.positiveId(id,"id");
        var context=shows.context(id).orElseThrow(ResourceNotFoundException::new);
        var movieIds=new TreeSet<Long>();movieIds.add(context.movieId());if(targetMovie!=null)movieIds.add(targetMovie);
        var roomIds=new TreeSet<Long>();roomIds.add(context.roomId());if(targetRoom!=null)roomIds.add(targetRoom);
        for(Long movie:movieIds) movies.findLocked(movie).orElseThrow(ResourceNotFoundException::new);
        for(Long room:roomIds) rooms.findLocked(room).orElseThrow(ResourceNotFoundException::new);
        Showtime show=shows.findLocked(id).orElseThrow(ResourceNotFoundException::new);
        if(!show.getMovieId().equals(context.movieId())||!show.getRoomId().equals(context.roomId()))
            throw new BusinessException("CONCURRENT_MODIFICATION","Ngữ cảnh suất vừa thay đổi; hãy tải lại trước khi thao tác.");
        return show;
    }
    private void apply(Showtime show,ShowtimeRequest request,Movie movie,Room room) {
        if(!movie.getStatus().equals("ACTIVE")) throw new BusinessException("MOVIE_INACTIVE","Phim đã ngừng sử dụng.");
        if(!room.getStatus().equals("ACTIVE")) throw new BusinessException("ROOM_INACTIVE","Phòng đã ngừng sử dụng.");
        long active=seats.counts(List.of(room.getId())).stream().mapToLong(SeatRepository.Counts::getActive).sum();
        if(active==0) throw new BusinessException("ROOM_NO_ACTIVE_SEATS","Phòng phải có ít nhất một ghế đang sử dụng.");
        LocalDateTime start=time(request.startsAt(),"startsAt",true);
        if(!start.isAfter(LocalDateTime.now(clock))) throw CatalogQuery.invalid("startsAt","Chỉ tạo/sửa thời điểm bắt đầu ở tương lai.");
        LocalDateTime end=start.plusMinutes(movie.getDurationMinutes());
        if(end.plusMinutes(15).getYear()>9999) throw CatalogQuery.invalid("startsAt","Thời gian kết thúc và khoảng nghỉ nằm ngoài miền DATETIME.");
        if(request.vipPrice().compareTo(request.standardPrice())<0) throw CatalogQuery.invalid("vipPrice","Giá VIP không được thấp hơn giá thường theo schema hiện có.");
        // Strict inequalities admit exactly 15 minutes. Current reads run AFTER obtaining the room lock.
        if(shows.overlaps(room.getId(),show.getId()==null?0L:show.getId(),start.minusMinutes(15),end.plusMinutes(15))>0)
            throw new BusinessException("SHOWTIME_CONFLICT","Suất trùng lịch hoặc chưa đủ 15 phút nghỉ trong phòng.");
        show.setMovieId(movie.getId());show.setRoomId(room.getId());show.setStartsAt(start);show.setEndsAt(end);
        show.setDurationMinutesSnapshot(movie.getDurationMinutes());show.setMinimumAgeSnapshot(movie.getMinimumAge());
        show.setStandardPrice(request.standardPrice());show.setVipPrice(request.vipPrice());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Long create(ShowtimeRequest request) {
        Movie movie=movies.findLocked(request.movieId()).orElseThrow(ResourceNotFoundException::new);
        Room room=rooms.findLocked(request.roomId()).orElseThrow(ResourceNotFoundException::new);
        Showtime show=new Showtime();show.setStatus("SCHEDULED");show.setPriceVersion(0L);
        apply(show,request,movie,room);return shows.saveAndFlush(show).getId();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Long update(Long id,ShowtimeRequest request) {
        Showtime show=locked(id,request.movieId(),request.roomId());editable(show);
        Movie movie=movies.findById(request.movieId()).orElseThrow(ResourceNotFoundException::new);
        Room room=rooms.findById(request.roomId()).orElseThrow(ResourceNotFoundException::new);
        boolean priceChanged=show.getStandardPrice().compareTo(request.standardPrice())!=0||show.getVipPrice().compareTo(request.vipPrice())!=0;
        apply(show,request,movie,room);if(priceChanged)show.setPriceVersion(Math.addExact(show.getPriceVersion(),1));
        shows.saveAndFlush(show);return id;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Long cancel(Long id,CancelShowtimeRequest request,Long actorId) {
        Showtime show=locked(id,null,null);editable(show);
        show.setStatus("CANCELLED");show.setCancelledAt(LocalDateTime.now(clock));show.setCancelledBy(actorId);show.setCancelReason(request.reason().strip());
        shows.saveAndFlush(show);return id;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void delete(Long id) { Showtime show=locked(id,null,null);editable(show);shows.delete(show);shows.flush(); }
}
