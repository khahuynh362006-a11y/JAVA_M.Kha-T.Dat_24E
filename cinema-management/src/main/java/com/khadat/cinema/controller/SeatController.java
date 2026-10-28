package com.khadat.cinema.controller;
import com.khadat.cinema.dto.seat.*;
import com.khadat.cinema.service.SeatService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/rooms/{roomId}/seats")
public class SeatController {
    private final SeatService service;
    public SeatController(SeatService service) { this.service=service; }
    @GetMapping public SeatMapResponse map(@PathVariable Long roomId) { return service.map(roomId); }
    @PostMapping public ResponseEntity<SeatResponse> create(@PathVariable Long roomId,@Valid @RequestBody SeatRequest request) {
        var r=service.create(roomId,request);return ResponseEntity.created(URI.create("/api/admin/rooms/"+roomId+"/seats")).body(r);
    }
    @PostMapping("/bulk") public ResponseEntity<SeatMapResponse> bulk(@PathVariable Long roomId,@Valid @RequestBody BulkSeatRequest request) {
        return ResponseEntity.created(URI.create("/api/admin/rooms/"+roomId+"/seats")).body(service.bulk(roomId,request));
    }
    @PutMapping("/{seatId}") public SeatResponse update(@PathVariable Long roomId,@PathVariable Long seatId,@Valid @RequestBody SeatRequest request) { return service.update(roomId,seatId,request); }
    @DeleteMapping("/{seatId}") public ResponseEntity<Void> delete(@PathVariable Long roomId,@PathVariable Long seatId) { service.delete(roomId,seatId);return ResponseEntity.noContent().build(); }
}
