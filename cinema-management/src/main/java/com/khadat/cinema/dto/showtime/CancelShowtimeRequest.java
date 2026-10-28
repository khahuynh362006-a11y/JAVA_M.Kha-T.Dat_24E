package com.khadat.cinema.dto.showtime;
import jakarta.validation.constraints.*;
public record CancelShowtimeRequest(@NotBlank @Size(max=500) String reason) { }
