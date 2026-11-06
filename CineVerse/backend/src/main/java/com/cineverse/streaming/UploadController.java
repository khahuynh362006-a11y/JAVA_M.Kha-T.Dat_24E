package com.cineverse.streaming;

import com.cineverse.security.CurrentUser;

import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class UploadController {
    private final UploadService uploads;
    private final CurrentUser current;

    public UploadController(UploadService uploads, CurrentUser current) {
        this.uploads = uploads;
        this.current = current;
    }

    @PostMapping("/admin/uploads")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file)
            throws java.io.IOException {
        return uploads.upload(file, false, current.id());
    }

    @PostMapping("/users/me/avatar")
    public Map<String, String> avatar(@RequestParam("file") MultipartFile file)
            throws java.io.IOException {
        return uploads.avatar(file, current.id());
    }

    @GetMapping("/assets/{filename}")
    public ResponseEntity<Resource> image(@PathVariable String filename)
            throws java.io.IOException {
        Path p = uploads.asset(filename);
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                Objects.requireNonNullElse(
                                        Files.probeContentType(p), "application/octet-stream")))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)))
                .body(new FileSystemResource(p));
    }
}
