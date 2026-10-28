package com.khadat.cinema.dto.showtime;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.khadat.cinema.dto.movie.StrictLongDeserializer;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record ShowtimeRequest(
    @NotNull @Positive @JsonDeserialize(using=StrictLongDeserializer.class) Long movieId,
    @NotNull @Positive @JsonDeserialize(using=StrictLongDeserializer.class) Long roomId,
    @NotNull @JsonFormat(without=JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime startsAt,
    @NotNull @DecimalMin("0") @Digits(integer=12,fraction=0) BigDecimal standardPrice,
    @NotNull @DecimalMin("0") @Digits(integer=12,fraction=0) BigDecimal vipPrice,
    @Null OffsetDateTime endsAt, @Null String status) { }
