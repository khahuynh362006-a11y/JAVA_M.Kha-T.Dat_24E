package com.khadat.cinema.controller;

import com.khadat.cinema.dto.PageResponse;
import com.khadat.cinema.dto.genre.*;
import com.khadat.cinema.service.GenreService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/genres")
public class GenreController {
    private final GenreService service;
    public GenreController(GenreService service) { this.service=service; }
    @GetMapping
    public PageResponse<GenreResponse> list(@RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="id") String sort, @RequestParam(defaultValue="asc") String direction) {
        return service.list(q,page,size,sort,direction);
    }
    @GetMapping("/{id}") public GenreResponse detail(@PathVariable Long id) { return service.detail(id); }
    @PostMapping public ResponseEntity<GenreResponse> create(@Valid @RequestBody GenreRequest request) {
        var result=service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/genres/"+result.id())).body(result);
    }
    @PutMapping("/{id}") public GenreResponse update(@PathVariable Long id,@Valid @RequestBody GenreRequest request) {
        return service.update(id,request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id); return ResponseEntity.noContent().build();
    }
}
