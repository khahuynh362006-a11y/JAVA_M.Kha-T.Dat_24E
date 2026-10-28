package com.khadat.cinema.controller;

import com.khadat.cinema.dto.PageResponse;
import com.khadat.cinema.dto.movie.*;
import com.khadat.cinema.service.MovieService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/movies")
public class MovieController {
    private final MovieService service;
    public MovieController(MovieService service) { this.service=service; }
    @GetMapping public PageResponse<MovieResponse> list(@RequestParam(defaultValue="") String q,
        @RequestParam(required=false) String status, @RequestParam(required=false) Long genreId,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="id") String sort, @RequestParam(defaultValue="asc") String direction) {
        return service.list(q,status,genreId,page,size,sort,direction);
    }
    @GetMapping("/{id}") public MovieResponse detail(@PathVariable Long id) { return service.detail(id); }
    @PostMapping public ResponseEntity<MovieResponse> create(@Valid @RequestBody MovieRequest request) {
        var result=service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/movies/"+result.id())).body(result);
    }
    @PutMapping("/{id}") public MovieResponse update(@PathVariable Long id,@Valid @RequestBody MovieRequest request) {
        return service.update(id,request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id); return ResponseEntity.noContent().build();
    }
}
