package com.cineverse.streaming;


import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.nio.file.*;

@RestController
@RequestMapping("/api/v1")
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @GetMapping("/movies/{id}/trailer")
    public MediaService.Playback trailer(@PathVariable Long id) {
        return media.trailer(id);
    }

    @GetMapping("/media/{ticket}/subtitle")
    public ResponseEntity<org.springframework.core.io.Resource> subtitle(
            @PathVariable String ticket) {
        var p = media.resolve(ticket, true);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/vtt;charset=UTF-8"))
                .cacheControl(CacheControl.noStore())
                .body(new org.springframework.core.io.FileSystemResource(p));
    }

    @GetMapping("/media/{ticket}/video")
    public ResponseEntity<StreamingResponseBody> video(
            @PathVariable String ticket,
            @RequestHeader(name = "Range", required = false) String range)
            throws IOException {
        Path p = media.resolve(ticket, false);
        long length = Files.size(p), start = 0, end = length - 1;
        boolean partial = range != null;
        if (range != null) {
            try {
                if (!range.matches("bytes=\\d*-\\d*") || range.equals("bytes=-"))
                    throw new IllegalArgumentException();
                String[] parts = range.substring(6).split("-", -1);
                if (parts[0].isEmpty()) {
                    long suffix = Long.parseLong(parts[1]);
                    if (suffix <= 0) throw new IllegalArgumentException();
                    start = Math.max(0, length - suffix);
                } else {
                    start = Long.parseLong(parts[0]);
                    if (!parts[1].isEmpty()) end = Math.min(end, Long.parseLong(parts[1]));
                }
                if (start >= length || start > end || start < 0)
                    throw new IllegalArgumentException();
            } catch (Exception e) {
                return ResponseEntity.status(416)
                        .header("Content-Range", "bytes */" + length)
                        .header("Accept-Ranges", "bytes")
                        .build();
            }
        }
        long first = start, count = end - start + 1;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("video/mp4"));
        headers.setContentLength(count);
        headers.set("Accept-Ranges", "bytes");
        headers.setCacheControl("private, no-store");
        headers.set("Referrer-Policy", "no-referrer");
        if (partial) headers.set("Content-Range", "bytes " + start + "-" + end + "/" + length);
        StreamingResponseBody body =
                out -> {
                    try (InputStream in = Files.newInputStream(p)) {
                        in.skipNBytes(first);
                        byte[] buffer = new byte[65536];
                        long left = count;
                        while (left > 0) {
                            int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                            if (n < 0) break;
                            out.write(buffer, 0, n);
                            left -= n;
                        }
                    }
                };
        return new ResponseEntity<>(
                body, headers, partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }
}
