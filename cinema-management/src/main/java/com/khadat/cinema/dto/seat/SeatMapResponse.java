package com.khadat.cinema.dto.seat;
import com.khadat.cinema.dto.room.RoomResponse;
import java.util.List;
public record SeatMapResponse(RoomResponse room,List<SeatResponse> seats) { }
