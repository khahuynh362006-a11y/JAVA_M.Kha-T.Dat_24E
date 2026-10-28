package com.khadat.cinema.dto.movie;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
import java.util.List;

public record MovieRequest(
    @NotBlank @Size(max=32) @Pattern(regexp="[A-Za-z0-9._-]+") String code,
    @NotBlank @Size(max=200) String title,
    @Size(max=65535) String description,
    @NotNull @Min(1) @Max(65535) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer durationMinutes,
    @NotNull @Min(0) @Max(18) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer minimumAge,
    @Size(max=2048) String posterUrl,
    @Size(max=2048) String trailerUrl,
    @NotNull @Pattern(regexp="ACTIVE|INACTIVE") String status,
    @NotEmpty @Size(max=100) @JsonDeserialize(contentUsing=StrictLongDeserializer.class)
    List<@NotNull @Positive Long> genreIds
) { }
