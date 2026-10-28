package com.khadat.cinema.controller;
import com.khadat.cinema.config.CinemaPrincipal;
import com.khadat.cinema.dto.PageResponse;
import com.khadat.cinema.dto.booking.*;
import com.khadat.cinema.service.BookingService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class BookingController {
    private final BookingService service;public BookingController(BookingService service){this.service=service;}
    @GetMapping("/api/showtimes/{id}/seats") public ShowtimeSeatMap map(@PathVariable Long id){return service.map(id);}
    @PostMapping("/api/bookings") public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication auth){
        var result=service.create(request,key,(CinemaPrincipal)auth.getPrincipal());return ResponseEntity.status(result.replayed()?200:201)
            .location(URI.create("/api/bookings/"+result.id())).header("Idempotency-Replayed",Boolean.toString(result.replayed())).body(service.detail(result.id()));
    }
    @GetMapping("/api/bookings") public PageResponse<BookingResponse> list(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long showtimeId,
        @RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="id") String sort,@RequestParam(defaultValue="asc") String direction){return service.list(q,showtimeId,status,page,size,sort,direction);}
    @GetMapping("/api/bookings/{id}") public BookingResponse detail(@PathVariable Long id){return service.detail(id);}
    @GetMapping("/api/bookings/by-code/{code}") public BookingResponse byCode(@PathVariable String code){return service.byCode(code);}
    @PostMapping("/api/bookings/{id}/cancel") public BookingResponse cancel(@PathVariable Long id,@Valid @RequestBody CancelBookingRequest request,Authentication auth){return service.detail(service.cancel(id,request,((CinemaPrincipal)auth.getPrincipal()).getId()));}
}
