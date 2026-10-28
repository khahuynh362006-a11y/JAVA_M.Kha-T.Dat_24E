package com.khadat.cinema.controller;
import com.khadat.cinema.config.CinemaPrincipal;
import com.khadat.cinema.dto.PageResponse;
import com.khadat.cinema.dto.showtime.*;
import com.khadat.cinema.mapper.ShowtimeMapper;
import com.khadat.cinema.service.ShowtimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class ShowtimeController {
    private final ShowtimeService service;private final Clock clock;
    public ShowtimeController(ShowtimeService service,Clock clock) { this.service=service;this.clock=clock; }
    @GetMapping({"/api/admin/showtimes","/api/showtimes"})
    public PageResponse<ShowtimeResponse> list(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(required=false) Long roomId,@RequestParam(required=false) Long movieId,@RequestParam(required=false) String status,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="startsAt") String sort,
        @RequestParam(defaultValue="asc") String direction) { return service.list(date,from,to,roomId,movieId,status,page,size,sort,direction); }
    @GetMapping({"/api/admin/showtimes/{id}","/api/showtimes/{id}"})
    public EntityModel<ShowtimeResponse> detail(@PathVariable Long id,Authentication auth,HttpServletRequest request) {
        return model(id,auth,request.getRequestURI().startsWith("/api/admin/")?"/api/admin/showtimes":"/api/showtimes");
    }
    private EntityModel<ShowtimeResponse> model(Long id,Authentication auth,String collection) {
        ShowtimeView view=service.detail(id);var model=EntityModel.of(ShowtimeMapper.response(view,clock));
        model.add(Link.of(collection+"/"+id,"self"),Link.of(collection,"collection"));
        boolean admin=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));
        if(admin) {
            model.add(Link.of("/api/admin/movies/"+view.movieId(),"movie"),Link.of("/api/admin/rooms/"+view.roomId(),"room"));
            if(view.status().equals("SCHEDULED") && view.startsAt().isAfter(LocalDateTime.now(clock)) && view.bookingCount()==0) {
                model.add(Link.of("/api/admin/showtimes/"+id,"update").withType("application/json"));
                model.add(Link.of("/api/admin/showtimes/"+id,"delete"));
                model.add(Link.of("/api/admin/showtimes/"+id+"/cancel","cancel").withType("application/json"));
            }
        }
        return model;
    }
    @PostMapping("/api/admin/showtimes") public ResponseEntity<EntityModel<ShowtimeResponse>> create(@Valid @RequestBody ShowtimeRequest request,Authentication auth) {
        Long id=service.create(request);return ResponseEntity.created(URI.create("/api/admin/showtimes/"+id)).body(model(id,auth,"/api/admin/showtimes"));
    }
    @PutMapping("/api/admin/showtimes/{id}") public EntityModel<ShowtimeResponse> update(@PathVariable Long id,@Valid @RequestBody ShowtimeRequest request,Authentication auth) {
        return model(service.update(id,request),auth,"/api/admin/showtimes");
    }
    @PostMapping("/api/admin/showtimes/{id}/cancel") public EntityModel<ShowtimeResponse> cancel(@PathVariable Long id,@Valid @RequestBody CancelShowtimeRequest request,Authentication auth) {
        return model(service.cancel(id,request,((CinemaPrincipal)auth.getPrincipal()).getId()),auth,"/api/admin/showtimes");
    }
    @DeleteMapping("/api/admin/showtimes/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id);return ResponseEntity.noContent().build(); }
}
