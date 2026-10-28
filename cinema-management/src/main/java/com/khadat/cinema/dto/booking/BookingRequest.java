package com.khadat.cinema.dto.booking;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.khadat.cinema.dto.movie.StrictLongDeserializer;
import jakarta.validation.constraints.*;
import java.util.List;
public record BookingRequest(
    @NotNull @Positive @JsonDeserialize(using=StrictLongDeserializer.class) Long showtimeId,
    @NotEmpty @Size(max=20) @JsonDeserialize(contentUsing=StrictLongDeserializer.class) List<@NotNull @Positive Long> seatIds,
    @NotNull @PositiveOrZero @JsonDeserialize(using=StrictLongDeserializer.class) Long priceVersion,
    @NotNull Boolean ageChecked) { }
