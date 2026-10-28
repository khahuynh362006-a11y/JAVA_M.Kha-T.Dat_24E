package com.khadat.cinema.mapper;
import com.khadat.cinema.dto.seat.*;
import com.khadat.cinema.entity.Seat;
import java.util.Locale;
public final class SeatMapper {
    private SeatMapper() { }
    public static String code(SeatRequest r) { return r.rowLabel().toUpperCase(Locale.ROOT)+r.seatNumber(); }
    public static void apply(SeatRequest r,Seat s) {
        s.setRowLabel(r.rowLabel().toUpperCase(Locale.ROOT));s.setSeatNumber(r.seatNumber());s.setCode(code(r));
        s.setSeatType(r.seatType());s.setStatus(r.status());
    }
    public static SeatResponse response(Seat s) {
        return new SeatResponse(s.getId(),s.getRoomId(),s.getCode(),s.getRowLabel(),s.getSeatNumber(),s.getSeatType(),s.getStatus());
    }
}
