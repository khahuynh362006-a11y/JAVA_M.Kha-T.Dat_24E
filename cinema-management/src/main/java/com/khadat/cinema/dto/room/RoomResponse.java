package com.khadat.cinema.dto.room;
import java.time.LocalDateTime;
public record RoomResponse(Long id,String code,String name,String status,long seatCount,long activeSeatCount,LocalDateTime createdAt) { }
