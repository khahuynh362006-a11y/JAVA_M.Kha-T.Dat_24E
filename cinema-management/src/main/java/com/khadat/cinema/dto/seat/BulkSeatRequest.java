package com.khadat.cinema.dto.seat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.khadat.cinema.dto.movie.StrictIntegerDeserializer;
import jakarta.validation.constraints.*;
import java.util.List;
public record BulkSeatRequest(@NotEmpty @Size(max=26) List<@NotBlank @Pattern(regexp="[A-Za-z]{1,4}") String> rows,
    @NotNull @Min(1) @Max(50) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer seatsPerRow,
    @NotNull @Pattern(regexp="STANDARD|VIP") String seatType) { }
