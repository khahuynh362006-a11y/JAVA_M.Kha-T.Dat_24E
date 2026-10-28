package com.khadat.cinema.dto.room;
import jakarta.validation.constraints.*;
public record RoomRequest(@NotBlank @Size(max=32) @Pattern(regexp="[A-Za-z0-9._-]+") String code,
    @NotBlank @Size(max=100) String name, @NotNull @Pattern(regexp="ACTIVE|INACTIVE") String status) { }
