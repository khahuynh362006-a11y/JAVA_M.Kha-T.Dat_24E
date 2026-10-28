package com.khadat.cinema.service;
import com.khadat.cinema.dto.seat.*;
import com.khadat.cinema.entity.*;
import com.khadat.cinema.exception.*;
import com.khadat.cinema.mapper.*;
import com.khadat.cinema.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @Transactional(readOnly=true)
public class SeatService {
    private final RoomService roomService;private final RoomRepository rooms;private final SeatRepository seats;private final Clock clock;
    public SeatService(RoomService roomService,RoomRepository rooms,SeatRepository seats,Clock clock) { this.roomService=roomService;this.rooms=rooms;this.seats=seats;this.clock=clock; }
    public SeatMapResponse map(Long roomId) {
        Room room=roomService.find(roomId,false);
        var list=seats.findByRoomId(roomId).stream().sorted(Comparator.comparingInt((Seat s)->s.getRowLabel().length())
            .thenComparing(Seat::getRowLabel).thenComparing(Seat::getSeatNumber).thenComparing(Seat::getId)).toList();
        return new SeatMapResponse(RoomMapper.response(room,list.size(),list.stream().filter(s->s.getStatus().equals("ACTIVE")).count()),list.stream().map(SeatMapper::response).toList());
    }
    private void structuralChange(Long roomId) {
        if(rooms.countShowtimes(roomId)>0) throw new BusinessException("SEAT_LAYOUT_LOCKED","Phòng đã có suất chiếu; không được thêm, xóa, đổi vị trí hoặc loại ghế.");
    }
    private void limit(Long roomId,long adding) {
        if(seats.countByRoomId(roomId)+adding>500) throw CatalogQuery.invalid("seats","Mỗi phòng tối đa 500 ghế theo giới hạn MVP.");
    }
    private void unique(Long roomId,SeatRequest r,Long excluded) {
        String row=r.rowLabel().toUpperCase(Locale.ROOT);
        if(seats.existsByRoomIdAndCodeAndIdNot(roomId,SeatMapper.code(r),excluded)
            ||seats.existsByRoomIdAndRowLabelAndSeatNumberAndIdNot(roomId,row,r.seatNumber(),excluded))
            throw new FieldValidationException(HttpStatus.CONFLICT,"SEAT_POSITION_EXISTS","seatNumber","Ghế hoặc vị trí này đã tồn tại trong phòng.");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SeatResponse create(Long roomId,SeatRequest request) {
        roomService.find(roomId,true);structuralChange(roomId);limit(roomId,1);unique(roomId,request,0L);
        Seat s=new Seat();s.setRoomId(roomId);SeatMapper.apply(request,s);return SeatMapper.response(seats.saveAndFlush(s));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SeatResponse update(Long roomId,Long seatId,SeatRequest request) {
        roomService.find(roomId,true);CatalogQuery.positiveId(seatId,"seatId");
        Seat seat=seats.findByIdAndRoomId(seatId,roomId).orElseThrow(ResourceNotFoundException::new);
        boolean structural=!seat.getCode().equals(SeatMapper.code(request)) || !seat.getRowLabel().equals(request.rowLabel().toUpperCase(Locale.ROOT))
            ||!seat.getSeatNumber().equals(request.seatNumber()) || !seat.getSeatType().equals(request.seatType());
        if(structural) structuralChange(roomId);
        if(!seat.getStatus().equals(request.status()) && rooms.countUnfinished(roomId,LocalDateTime.now(clock))>0)
            throw new BusinessException("SEAT_STATUS_LOCKED","Phòng còn suất chưa hủy chưa kết thúc; chưa được đổi trạng thái ghế.");
        unique(roomId,request,seatId);SeatMapper.apply(request,seat);return SeatMapper.response(seats.saveAndFlush(seat));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void delete(Long roomId,Long seatId) {
        roomService.find(roomId,true);CatalogQuery.positiveId(seatId,"seatId");
        Seat seat=seats.findByIdAndRoomId(seatId,roomId).orElseThrow(ResourceNotFoundException::new);
        structuralChange(roomId);seats.delete(seat);seats.flush();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SeatMapResponse bulk(Long roomId,BulkSeatRequest request) {
        roomService.find(roomId,true);structuralChange(roomId);
        if(seats.countByRoomId(roomId)>0) throw new BusinessException("SEAT_LAYOUT_NOT_EMPTY","Chỉ tạo hàng loạt khi phòng chưa có ghế.");
        List<String> rows=request.rows().stream().map(r->r.toUpperCase(Locale.ROOT)).toList();
        if(new HashSet<>(rows).size()!=rows.size()) throw CatalogQuery.invalid("rows","Danh sách hàng không được trùng.");
        limit(roomId,(long)rows.size()*request.seatsPerRow());
        List<Seat> created=new ArrayList<>();
        for(String row:rows) for(int n=1;n<=request.seatsPerRow();n++) {
            Seat seat=new Seat();seat.setRoomId(roomId);SeatMapper.apply(new SeatRequest(row,n,request.seatType(),"ACTIVE"),seat);created.add(seat);
        }
        seats.saveAll(created);seats.flush();return map(roomId);
    }
}
