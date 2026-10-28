package com.khadat.cinema.repository.jdbc;
import com.khadat.cinema.dto.statistics.StatisticsRows.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class StatisticsJdbcRepository {
    private final JdbcTemplate jdbc;
    public StatisticsJdbcRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private record Filter(String sql,List<Object> args) { }
    private Filter filter(LocalDateTime from,LocalDateTime to,Long room,Long movie,String time) {
        String sql=" WHERE "+time+">=? AND "+time+"<?";var args=new ArrayList<Object>();args.add(from);args.add(to);
        if(room!=null){sql+=" AND s.room_id=?";args.add(room);}if(movie!=null){sql+=" AND s.movie_id=?";args.add(movie);}return new Filter(sql,args);
    }
    public List<ShowtimesDay> showtimes(LocalDateTime from,LocalDateTime to,Long room,Long movie){
        var f=filter(from,to,room,movie,"s.starts_at");
        return jdbc.query("SELECT DATE(s.starts_at) day,COUNT(*) total,SUM(s.status='SCHEDULED') scheduled,SUM(s.status='CANCELLED') cancelled FROM showtimes s"+f.sql+" GROUP BY DATE(s.starts_at) ORDER BY day",
            (rs,n)->new ShowtimesDay(rs.getDate("day").toLocalDate(),rs.getLong("total"),rs.getLong("scheduled"),rs.getLong("cancelled")),f.args.toArray());
    }
    public List<CancellationDay> cancellations(LocalDateTime from,LocalDateTime to,Long room,Long movie){
        var f=filter(from,to,room,movie,"b.cancelled_at");
        return jdbc.query("SELECT DATE(b.cancelled_at) day,COUNT(*) bookings,COALESCE(SUM(d.seats),0) seats,COALESCE(SUM(b.total_amount),0) value FROM bookings b JOIN showtimes s ON s.id=b.showtime_id LEFT JOIN (SELECT booking_id,COUNT(*) seats FROM booking_seats GROUP BY booking_id) d ON d.booking_id=b.id"+f.sql+" AND b.status='CANCELLED' GROUP BY DATE(b.cancelled_at) ORDER BY day",
            (rs,n)->new CancellationDay(rs.getDate("day").toLocalDate(),rs.getLong("bookings"),rs.getLong("seats"),rs.getBigDecimal("value")),f.args.toArray());
    }
    public long movieTotal(LocalDateTime from,LocalDateTime to,Long room,Long movie){
        var f=filter(from,to,room,movie,"s.starts_at");return jdbc.queryForObject("SELECT COUNT(DISTINCT s.movie_id) FROM showtimes s"+f.sql+" AND s.status='SCHEDULED'",Long.class,f.args.toArray());
    }
    public List<BookedSeatsMovie> bookedSeats(LocalDateTime from,LocalDateTime to,Long room,Long movie,int size,long offset){
        var f=filter(from,to,room,movie,"s.starts_at");var args=new ArrayList<>(f.args);args.add(size);args.add(offset);
        return jdbc.query("SELECT s.movie_id,m.title,COALESCE(SUM(b.bookings),0) bookings,COALESCE(SUM(d.seats),0) seats,COALESCE(SUM(b.value),0) value FROM showtimes s JOIN movies m ON m.id=s.movie_id LEFT JOIN (SELECT showtime_id,COUNT(*) bookings,SUM(total_amount) value FROM bookings WHERE status='CONFIRMED' GROUP BY showtime_id) b ON b.showtime_id=s.id LEFT JOIN (SELECT d.showtime_id,COUNT(*) seats FROM booking_seats d JOIN bookings b ON b.id=d.booking_id WHERE d.booking_status='CONFIRMED' AND b.status='CONFIRMED' GROUP BY d.showtime_id) d ON d.showtime_id=s.id"+f.sql+" AND s.status='SCHEDULED' GROUP BY s.movie_id,m.title ORDER BY s.movie_id LIMIT ? OFFSET ?",
            (rs,n)->new BookedSeatsMovie(rs.getLong("movie_id"),rs.getString("title"),rs.getLong("bookings"),rs.getLong("seats"),rs.getBigDecimal("value")),args.toArray());
    }
    public long occupancyTotal(LocalDateTime from,LocalDateTime to,Long room,Long movie){
        var f=filter(from,to,room,movie,"s.starts_at");return jdbc.queryForObject("SELECT COUNT(*) FROM showtimes s"+f.sql+" AND s.status='SCHEDULED'",Long.class,f.args.toArray());
    }
    public List<OccupancyRaw> occupancy(LocalDateTime from,LocalDateTime to,Long room,Long movie,int size,long offset){
        var f=filter(from,to,room,movie,"s.starts_at");var args=new ArrayList<>(f.args);args.add(size);args.add(offset);
        return jdbc.query("SELECT s.id,s.movie_id,m.title,s.room_id,r.code,s.starts_at,s.capacity_snapshot,(SELECT COUNT(*) FROM booking_seats d JOIN bookings b ON b.id=d.booking_id WHERE d.showtime_id=s.id AND d.booking_status='CONFIRMED' AND b.status='CONFIRMED') booked,(SELECT COUNT(*) FROM seats x WHERE x.room_id=s.room_id AND x.status='ACTIVE') current_seats FROM showtimes s JOIN movies m ON m.id=s.movie_id JOIN rooms r ON r.id=s.room_id"+f.sql+" AND s.status='SCHEDULED' ORDER BY s.starts_at,s.id LIMIT ? OFFSET ?",
            (rs,n)->new OccupancyRaw(rs.getLong("id"),rs.getLong("movie_id"),rs.getString("title"),rs.getLong("room_id"),rs.getString("code"),rs.getTimestamp("starts_at").toLocalDateTime(),rs.getLong("booked"),rs.getObject("capacity_snapshot",Integer.class),rs.getLong("current_seats")),args.toArray());
    }
}
