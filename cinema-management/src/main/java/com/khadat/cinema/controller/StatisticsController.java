package com.khadat.cinema.controller;
import com.khadat.cinema.dto.statistics.StatisticsResponse;
import com.khadat.cinema.service.StatisticsService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/statistics")
public class StatisticsController {
    private final StatisticsService service;public StatisticsController(StatisticsService service){this.service=service;}
    @GetMapping({"/showtimes","/booked-seats","/occupancy","/cancellations"})
    public StatisticsResponse<?> query(jakarta.servlet.http.HttpServletRequest request,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required=false) Long roomId,@RequestParam(required=false) Long movieId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return service.query(request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/')+1),from,to,roomId,movieId,page,size);
    }
}
