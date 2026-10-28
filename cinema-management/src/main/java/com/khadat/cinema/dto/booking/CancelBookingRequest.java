package com.khadat.cinema.dto.booking;
import jakarta.validation.constraints.*;
public record CancelBookingRequest(@NotBlank @Size(max=500) String reason) { }
