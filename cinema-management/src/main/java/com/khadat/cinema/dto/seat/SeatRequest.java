package com.khadat.cinema.dto.seat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.khadat.cinema.dto.movie.StrictIntegerDeserializer;
import jakarta.validation.constraints.*;
public record SeatRequest(@NotBlank @Pattern(regexp="[A-Za-z]{1,4}") String rowLabel,
    @NotNull @Min(1) @Max(65535) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer seatNumber,
    @NotNull @Pattern(regexp="STANDARD|VIP") String seatType,
    @NotNull @Pattern(regexp="ACTIVE|INACTIVE") String status) { }
