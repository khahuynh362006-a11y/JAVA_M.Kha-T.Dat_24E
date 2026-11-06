package com.cineverse.streaming;

import com.cineverse.shared.*;
import com.cineverse.users.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class UploadService {
    private final Path media, assets;
    private final UserRepository users;
    private final Audit audit;

    public UploadService(
            @Value("${app.media-root}") String media,
            @Value("${app.upload-root}") String assets,
            UserRepository users,
            Audit audit)
            throws IOException {
        this.media = Path.of(media).toAbsolutePath().normalize();
        this.assets = Path.of(assets).toAbsolutePath().normalize();
        this.users = users;
        this.audit = audit;
        Files.createDirectories(this.media);
        Files.createDirectories(this.assets);
    }

    public Map<String, String> upload(MultipartFile f, boolean imageOnly, Long actor)
            throws IOException {
        String original = Objects.toString(f.getOriginalFilename(), "");
        String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (f.isEmpty() || f.getSize() > 250L * 1024 * 1024)
            throw ApiException.bad("Tệp rỗng hoặc vượt quá 250 MB.");
        boolean image = Set.of("jpg", "jpeg", "png", "webp").contains(ext);
        if ((imageOnly && !image) || (!image && !Set.of("mp4", "vtt").contains(ext)))
            throw ApiException.bad("Chỉ chấp nhận JPG, PNG, WebP, MP4 hoặc VTT.");
        if (image && f.getSize() > 8L * 1024 * 1024) throw ApiException.bad("Ảnh tối đa 8 MB.");
        byte[] head;
        try (var in = f.getInputStream()) {
            head = in.readNBytes(16);
        }
        boolean valid =
                switch (ext) {
                    case "jpg", "jpeg" ->
                            head.length > 2 && (head[0] & 255) == 255 && (head[1] & 255) == 216;
                    case "png" ->
                            head.length > 8
                                    && head[0] == (byte) 137
                                    && head[1] == 80
                                    && head[2] == 78
                                    && head[3] == 71;
                    case "webp" ->
                            head.length >= 12
                                    && new String(
                                                    head,
                                                    0,
                                                    4,
                                                    java.nio.charset.StandardCharsets.US_ASCII)
                                            .equals("RIFF")
                                    && new String(
                                                    head,
                                                    8,
                                                    4,
                                                    java.nio.charset.StandardCharsets.US_ASCII)
                                            .equals("WEBP");
                    case "mp4" ->
                            head.length >= 12
                                    && new String(
                                                    head,
                                                    4,
                                                    4,
                                                    java.nio.charset.StandardCharsets.US_ASCII)
                                            .equals("ftyp");
                    case "vtt" ->
                            new String(head, java.nio.charset.StandardCharsets.UTF_8)
                                    .startsWith("WEBVTT");
                    default -> false;
                };
        if (!valid) throw ApiException.bad("Nội dung tệp không khớp định dạng.");
        String name = UUID.randomUUID() + "." + ext;
        Path target = (image ? assets : media).resolve(name);
        try (var in = f.getInputStream()) {
            Files.copy(in, target);
        }
        audit.log(actor, "UPLOAD", "FILE", name, ext + " " + f.getSize() + " bytes");
        return Map.of("filename", name, "url", image ? "/api/v1/assets/" + name : name);
    }

    @Transactional
    public Map<String, String> avatar(MultipartFile f, Long actor) throws IOException {
        var result = upload(f, true, actor);
        User u = users.lockById(actor).orElseThrow(ApiException::missing);
        u.avatar = result.get("url");
        return result;
    }

    public Path asset(String filename) {
        if (!filename.matches("[a-f0-9-]+\\.(jpg|jpeg|png|webp)")) throw ApiException.missing();
        Path p = assets.resolve(filename).normalize();
        if (!p.startsWith(assets) || !Files.isRegularFile(p)) throw ApiException.missing();
        return p;
    }
}
