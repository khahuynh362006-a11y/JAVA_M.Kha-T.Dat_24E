package com.khadat.cinema.service;
import com.khadat.cinema.dto.statistics.*;
import com.khadat.cinema.dto.statistics.StatisticsRows.*;
import com.khadat.cinema.mapper.ShowtimeMapper;
import com.khadat.cinema.repository.jdbc.StatisticsJdbcRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class StatisticsService {
    private final StatisticsJdbcRepository jdbc;private final Clock clock;
    public StatisticsService(StatisticsJdbcRepository jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public StatisticsResponse<?> query(String metric,LocalDate from,LocalDate to,Long room,Long movie,int page,int size){
        if(from==null||to==null||from.getYear()<1000||to.getYear()>9998||from.isAfter(to)||to.toEpochDay()-from.toEpochDay()>=31)
            throw CatalogQuery.invalid("to","Cần khoảng from/to hợp lệ, tối đa31 ngày kể cả hai đầu.");
        CatalogQuery.page(page,size,"id","asc",Set.of("id"));if(room!=null)CatalogQuery.positiveId(room,"roomId");if(movie!=null)CatalogQuery.positiveId(movie,"movieId");
        LocalDateTime lower=from.atStartOfDay(),upper=to.plusDays(1).atStartOfDay();var now=OffsetDateTime.now(clock);long offset=(long)page*size;
        List<?> rows;long total;String basis="SHOWTIME_START_DATE";
        if(metric.equals("showtimes")){
            Map<LocalDate,ShowtimesDay> found=new HashMap<>();jdbc.showtimes(lower,upper,room,movie).forEach(r->found.put(r.date(),r));
            var all=from.datesUntil(to.plusDays(1)).map(d->found.getOrDefault(d,new ShowtimesDay(d,0,0,0))).toList();total=all.size();rows=all.stream().skip(offset).limit(size).toList();
        }else if(metric.equals("cancellations")){
            basis="CANCELLATION_DATE";Map<LocalDate,CancellationDay> found=new HashMap<>();jdbc.cancellations(lower,upper,room,movie).forEach(r->found.put(r.date(),r));
            var all=from.datesUntil(to.plusDays(1)).map(d->found.getOrDefault(d,new CancellationDay(d,0,0,BigDecimal.ZERO))).toList();total=all.size();rows=all.stream().skip(offset).limit(size).toList();
        }else if(metric.equals("booked-seats")){
            total=jdbc.movieTotal(lower,upper,room,movie);rows=jdbc.bookedSeats(lower,upper,room,movie,size,offset);
        }else{
            total=jdbc.occupancyTotal(lower,upper,room,movie);rows=jdbc.occupancy(lower,upper,room,movie,size,offset).stream().map(r->{
                Long capacity;String source;
                if(r.snapshot()!=null){capacity=r.snapshot().longValue();source="SNAPSHOT";}
                else if(r.startsAt().isAfter(now.toLocalDateTime())){capacity=r.currentActiveSeats();source="CURRENT_ACTIVE_SEATS_PROVISIONAL";}
                else{capacity=null;source="MISSING_HISTORICAL_CAPACITY";}
                BigDecimal percent=capacity==null||capacity==0?null:BigDecimal.valueOf(r.bookedSeats()).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(capacity),2,RoundingMode.HALF_UP);
                return new Occupancy(r.showtimeId(),r.movieId(),r.movieTitle(),r.roomId(),r.roomCode(),r.startsAt().atOffset(ShowtimeMapper.OFFSET),r.bookedSeats(),capacity,source,percent,capacity!=null&&r.bookedSeats()>capacity);
            }).toList();
        }
        return new StatisticsResponse<>(from,to,"Asia/Ho_Chi_Minh",basis,now,rows,total,page,size,(int)((total+size-1)/size));
    }
}
