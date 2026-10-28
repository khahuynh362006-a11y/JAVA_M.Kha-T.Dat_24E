package com.khadat.cinema.service;
import com.khadat.cinema.dto.PageResponse;
import com.khadat.cinema.dto.room.*;
import com.khadat.cinema.entity.Room;
import com.khadat.cinema.exception.*;
import com.khadat.cinema.mapper.RoomMapper;
import com.khadat.cinema.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @Transactional(readOnly=true)
public class RoomService {
    private final RoomRepository rooms; private final SeatRepository seats; private final Clock clock;
    public RoomService(RoomRepository rooms,SeatRepository seats,Clock clock) { this.rooms=rooms;this.seats=seats;this.clock=clock; }
    public PageResponse<RoomResponse> list(String q,String status,int page,int size,String sort,String direction) {
        String term=CatalogQuery.term(q);
        if(status!=null && !Set.of("ACTIVE","INACTIVE").contains(status)) throw CatalogQuery.invalid("status","Trạng thái phải là ACTIVE hoặc INACTIVE.");
        var result=rooms.findAll((root,query,cb) -> cb.and(
            term.isEmpty()?cb.conjunction():cb.or(cb.like(root.get("code"),CatalogQuery.contains(term),'!'),cb.like(root.get("name"),CatalogQuery.contains(term),'!')),
            status==null?cb.conjunction():cb.equal(root.get("status"),status)),
            CatalogQuery.page(page,size,sort,direction,Set.of("id","code","name","status","createdAt")));
        return PageResponse.from(result,responses(result.getContent()));
    }
    public RoomResponse detail(Long id) { return responses(List.of(find(id,false))).getFirst(); }
    Room find(Long id,boolean locked) {
        CatalogQuery.positiveId(id,"roomId");
        return (locked?rooms.findLocked(id):rooms.findById(id)).orElseThrow(ResourceNotFoundException::new);
    }
    List<RoomResponse> responses(List<Room> list) {
        if(list.isEmpty()) return List.of();
        Map<Long,SeatRepository.Counts> counts=new HashMap<>();
        seats.counts(list.stream().map(Room::getId).toList()).forEach(c -> counts.put(c.getRoomId(),c));
        return list.stream().map(r -> { var c=counts.get(r.getId()); return RoomMapper.response(r,c==null?0:c.getTotal(),c==null?0:c.getActive()); }).toList();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RoomResponse create(RoomRequest request) {
        if(rooms.existsByCode(request.code())) throw duplicate();
        Room room=new Room();RoomMapper.apply(request,room);rooms.saveAndFlush(room);
        return RoomMapper.response(room,0,0);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RoomResponse update(Long id,RoomRequest request) {
        Room room=find(id,true);
        if(!room.getStatus().equals(request.status()) && rooms.countUnfinished(id,LocalDateTime.now(clock))>0)
            throw new BusinessException("ROOM_HAS_UNFINISHED_SHOWTIMES","Phòng còn suất chưa hủy chưa kết thúc; chưa được đổi trạng thái.");
        if(rooms.existsByCodeAndIdNot(request.code(),id)) throw duplicate();
        RoomMapper.apply(request,room);rooms.saveAndFlush(room);return responses(List.of(room)).getFirst();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void delete(Long id) {
        Room room=find(id,true);
        if(rooms.countShowtimes(id)>0) throw new BusinessException("ROOM_HAS_SHOWTIMES","Không thể xóa phòng đã có suất chiếu.");
        if(seats.countByRoomId(id)>0) throw new BusinessException("ROOM_HAS_SEATS","Phòng còn ghế; hãy xóa từng ghế hợp lệ trước.");
        rooms.delete(room);rooms.flush();
    }
    private FieldValidationException duplicate() { return new FieldValidationException(HttpStatus.CONFLICT,"ROOM_CODE_EXISTS","code","Mã phòng đã tồn tại."); }
}
