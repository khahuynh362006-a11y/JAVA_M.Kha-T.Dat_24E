package com.khadat.cinema.dto.seat;
public record SeatResponse(Long id,Long roomId,String code,String rowLabel,Integer seatNumber,String seatType,String status) { }
