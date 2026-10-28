package com.khadat.cinema.controller;
import com.khadat.cinema.dto.PageResponse;
import com.khadat.cinema.dto.room.*;
import com.khadat.cinema.service.RoomService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/rooms")
public class RoomController {
    private final RoomService service;
    public RoomController(RoomService service) { this.service=service; }
    @GetMapping public PageResponse<RoomResponse> list(@RequestParam(defaultValue="") String q,@RequestParam(required=false) String status,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="id") String sort,@RequestParam(defaultValue="asc") String direction) {
        return service.list(q,status,page,size,sort,direction);
    }
    @GetMapping("/{id}") public RoomResponse detail(@PathVariable Long id) { return service.detail(id); }
    @PostMapping public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomRequest request) {
        var r=service.create(request);return ResponseEntity.created(URI.create("/api/admin/rooms/"+r.id())).body(r);
    }
    @PutMapping("/{id}") public RoomResponse update(@PathVariable Long id,@Valid @RequestBody RoomRequest request) { return service.update(id,request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id);return ResponseEntity.noContent().build(); }
}
