package com.khadat.cinema.mapper;
import com.khadat.cinema.dto.room.*;
import com.khadat.cinema.entity.Room;
import java.util.Locale;
public final class RoomMapper {
    private RoomMapper() { }
    public static void apply(RoomRequest request,Room room) {
        room.setCode(request.code().toUpperCase(Locale.ROOT));room.setName(request.name().strip());room.setStatus(request.status());
    }
    public static RoomResponse response(Room r,long total,long active) {
        return new RoomResponse(r.getId(),r.getCode(),r.getName(),r.getStatus(),total,active,r.getCreatedAt());
    }
}
