package com.khadat.cinema.dto.statistics;
import java.time.*;
import java.util.List;
public record StatisticsResponse<T>(LocalDate from,LocalDate to,String timeZone,String timeBasis,OffsetDateTime asOf,
    List<T> items,long total,int page,int size,int totalPages) { }
