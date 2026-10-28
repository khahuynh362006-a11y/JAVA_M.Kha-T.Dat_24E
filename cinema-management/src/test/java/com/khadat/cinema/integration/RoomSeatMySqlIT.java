package com.khadat.cinema.integration;

import com.fasterxml.jackson.databind.*;
import com.khadat.cinema.entity.Seat;
import com.khadat.cinema.repository.SeatRepository;
import java.net.*;
import java.net.http.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="CINEMA_MYSQL_IT",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoomSeatMySqlIT {
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired PasswordEncoder encoder;
    @MockitoSpyBean SeatRepository seats;
    @LocalServerPort int port;
    final String suffix=UUID.randomUUID().toString().replace("-","").substring(0,16);
    final String prefix="RS-"+suffix;
    final String adminEmail="room-admin-"+suffix+"@example.invalid",staffEmail="room-staff-"+suffix+"@example.invalid";
    final String password=secret();
    final List<Long> roomIds=new ArrayList<>();final Set<Long> historicalRooms=new HashSet<>();
    Browser admin,staff;int serial;long historyUser;
    static String secret() { byte[] b=new byte[24];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    @BeforeAll void setup() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).startsWith("cinema_management_test_");
        for(String email:List.of(adminEmail,staffEmail)) {
            jdbc.update("INSERT INTO users(username,email,display_name,password_hash,status) VALUES(?,?,?,?, 'ACTIVE')",email.substring(0,email.indexOf('@')),email,"Room test",encoder.encode(password));
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u JOIN roles r ON r.code=? WHERE u.email=?",email.equals(adminEmail)?"ADMIN":"STAFF",email);
        }
        admin=login(adminEmail);staff=login(staffEmail);
    }
    @AfterEach void cleanupRooms() {
        reset(seats);
        for(long room:roomIds) if(!historicalRooms.contains(room)) {
            jdbc.update("DELETE FROM showtimes WHERE room_id=?",room);
            jdbc.update("DELETE FROM seats WHERE room_id=?",room);
            jdbc.update("DELETE FROM rooms WHERE id=?",room);
        }
        roomIds.removeIf(id->!historicalRooms.contains(id));
    }
    @AfterAll void cleanupAccounts() {
        for(String email:List.of(adminEmail,staffEmail)) {
            jdbc.update("DELETE ur FROM user_roles ur JOIN users u ON u.id=ur.user_id WHERE u.email=?",email);
            jdbc.update("DELETE FROM users WHERE email=?",email);
        }
        // Non-login history user and booking fixtures intentionally remain: history triggers prohibit DELETE.
    }
    class Browser {
        final CookieManager jar=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(jar).followRedirects(HttpClient.Redirect.NEVER).build();String csrf;
        HttpResponse<String> call(String method,String path,Object body,boolean token) throws Exception {
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Accept","application/json");
            if(token && csrf!=null)b.header("X-CSRF-TOKEN",csrf);
            if(body!=null)b.header("Content-Type","application/json");
            return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        void token() throws Exception { csrf=body(call("GET","/api/auth/csrf",null,false),200).get("token").asText(); }
    }
    Browser login(String email) throws Exception {
        Browser b=new Browser();b.token();body(b.call("POST","/api/auth/login",Map.of("email",email,"password",password),true),200);b.token();return b;
    }
    JsonNode body(HttpResponse<String> response,int status) throws Exception {
        assertThat(response.statusCode()).as("HTTP %s: %s",status,response.body()).isEqualTo(status);
        return json.readTree(response.body());
    }
    JsonNode error(HttpResponse<String> response,int status,String code) throws Exception {
        JsonNode b=body(response,status);assertThat(b.get("code").asText()).isEqualTo(code);
        assertThat(b.get("fieldErrors").isObject()).isTrue();assertThat(b.has("trace")||b.has("exception")||response.body().contains(password)).isFalse();return b;
    }
    String path(long room) { return "/api/admin/rooms/"+room; }
    String layout(long room) { return path(room)+"/seats"; }
    Map<String,Object> roomRequest() { return new LinkedHashMap<>(Map.of("code",prefix+"-"+(++serial),"name","Room "+serial,"status","ACTIVE")); }
    long room() throws Exception { return room(roomRequest()); }
    long room(Map<String,Object> p) throws Exception {
        var r=admin.call("POST","/api/admin/rooms",p,true);long id=body(r,201).get("id").asLong();roomIds.add(id);
        assertThat(r.headers().firstValue("location")).hasValue(path(id));return id;
    }
    Map<String,Object> seat(String row,int n,String type,String status) { return Map.of("rowLabel",row,"seatNumber",n,"seatType",type,"status",status); }
    long seat(long room,String row,int n) throws Exception { return body(admin.call("POST",layout(room),seat(row,n,"STANDARD","ACTIVE"),true),201).get("id").asLong(); }
    Map<String,Object> bulk(List<String> rows,int n) { return Map.of("rows",rows,"seatsPerRow",n,"seatType","STANDARD"); }
    long count(long room) { return jdbc.queryForObject("SELECT COUNT(*) FROM seats WHERE room_id=?",Long.class,room); }
    @Test void roomCrudSearchPaginationAndCounts() throws Exception {
        var payload=roomRequest();payload.put("name",prefix+" Search");long id=room(payload);seat(id,"A",1);
        payload.put("name",prefix+" Changed");payload.put("status","INACTIVE");
        JsonNode updated=body(admin.call("PUT",path(id),payload,true),200);
        assertThat(updated.get("seatCount").asInt()).isEqualTo(1);assertThat(updated.get("activeSeatCount").asInt()).isEqualTo(1);
        JsonNode page=body(admin.call("GET","/api/admin/rooms?q="+payload.get("code")+"&status=INACTIVE&size=1&sort=name&direction=desc",null,false),200);
        assertThat(page.get("total").asInt()).isEqualTo(1);
        error(admin.call("DELETE",path(id),null,true),409,"ROOM_HAS_SEATS");
        long sid=body(admin.call("GET",layout(id),null,false),200).get("seats").get(0).get("id").asLong();
        assertThat(admin.call("DELETE",layout(id)+"/"+sid,null,true).statusCode()).isEqualTo(204);
        assertThat(admin.call("DELETE",path(id),null,true).statusCode()).isEqualTo(204);
        error(admin.call("GET",path(id),null,false),404,"RESOURCE_NOT_FOUND");
    }
    @Test void duplicateRoomCodeAndValidation() throws Exception {
        var p=roomRequest();room(p);p.put("code",p.get("code").toString().toLowerCase());
        assertThat(error(admin.call("POST","/api/admin/rooms",p,true),409,"ROOM_CODE_EXISTS").get("fieldErrors").has("code")).isTrue();
        for(var bad:List.of(Map.entry("name"," "),Map.entry("code","x".repeat(33)),Map.entry("status","MAINTENANCE"))) {
            var invalid=roomRequest();invalid.put(bad.getKey(),bad.getValue());error(admin.call("POST","/api/admin/rooms",invalid,true),400,"VALIDATION_FAILED");
        }
        for(String q:List.of("page=-1","size=101","sort=seatCount","direction=bad","status=BAD"))
            error(admin.call("GET","/api/admin/rooms?"+q,null,false),400,"VALIDATION_FAILED");
    }
    @Test void separateRoomsSameA1AndSeatUniqueness() throws Exception {
        long a=room(),b=room();seat(a,"a",1);seat(b,"A",1);
        error(admin.call("POST",layout(a),seat("A",1,"VIP","ACTIVE"),true),409,"SEAT_POSITION_EXISTS");
        assertThat(count(a)).isEqualTo(1);assertThat(count(b)).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update("INSERT INTO seats(room_id,code,row_label,seat_number,seat_type,status) VALUES(?,'A1','A',1,'STANDARD','ACTIVE')",a)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void cannotMoveOrDeleteSeatThroughAnotherRoom() throws Exception {
        long a=room(),b=room(),s=seat(b,"A",1);
        error(admin.call("PUT",layout(a)+"/"+s,seat("B",2,"VIP","ACTIVE"),true),404,"RESOURCE_NOT_FOUND");
        error(admin.call("DELETE",layout(a)+"/"+s,null,true),404,"RESOURCE_NOT_FOUND");
        assertThat(body(admin.call("GET",layout(b),null,false),200).get("seats").get(0).get("code").asText()).isEqualTo("A1");
    }
    @Test void seatUpdateValidationAndNaturalOrder() throws Exception {
        long r=room();long id=seat(r,"A",10);seat(r,"A",2);seat(r,"B",1);seat(r,"AA",1);
        JsonNode map=body(admin.call("GET",layout(r),null,false),200);
        assertThat(map.get("seats").findValuesAsText("code")).containsExactly("A2","A10","B1","AA1");
        body(admin.call("PUT",layout(r)+"/"+id,seat("A",10,"VIP","INACTIVE"),true),200);
        assertThat(body(admin.call("GET",path(r),null,false),200).get("activeSeatCount").asInt()).isEqualTo(3);
        for(var invalid:List.of(seat("1",1,"STANDARD","ACTIVE"),seat("ABCDE",1,"STANDARD","ACTIVE"),seat("A",0,"STANDARD","ACTIVE"),seat("A",65536,"STANDARD","ACTIVE"),seat("A",1,"BROKEN","ACTIVE"),seat("A",1,"VIP","BOOKED")))
            error(admin.call("POST",layout(r),invalid,true),400,"VALIDATION_FAILED");
        error(admin.call("POST",layout(r),Map.of("rowLabel","A","seatNumber",1.5,"seatType","STANDARD","status","ACTIVE"),true),400,"INVALID_REQUEST");
    }
    @Test void bulkSuccessReplayAndBothRooms() throws Exception {
        long a=room(),b=room();
        for(long r:List.of(a,b)) {
            JsonNode result=body(admin.call("POST",layout(r)+"/bulk",bulk(List.of("A","B"),3),true),201);
            assertThat(result.get("room").get("seatCount").asInt()).isEqualTo(6);
            assertThat(result.get("seats").size()).isEqualTo(6);
            error(admin.call("POST",layout(r)+"/bulk",bulk(List.of("A","B"),3),true),409,"SEAT_LAYOUT_NOT_EMPTY");
            assertThat(count(r)).isEqualTo(6);
        }
    }
    @Test void bulkInvalidRequestLeavesRoomEmpty() throws Exception {
        long r=room();
        for(var request:List.of(bulk(List.of("A","a"),3),bulk(List.of("1"),3),bulk(List.of(),3),bulk(List.of("A"),51),bulk(List.of("A","B","C","D","E","F","G","H","I","J","K"),50))) {
            error(admin.call("POST",layout(r)+"/bulk",request,true),400,"VALIDATION_FAILED");assertThat(count(r)).isZero();
        }
    }
    @Test void bulkRollsBackEvenAfterDatabaseInsert() throws Exception {
        long r=room();
        doAnswer(call->{ List<Seat> created=call.getArgument(0);seats.saveAndFlush(created.getFirst());throw new DataIntegrityViolationException("test-only injected failure after flush"); }).when(seats).saveAll(any());
        try { error(admin.call("POST",layout(r)+"/bulk",bulk(List.of("A","B"),3),true),409,"DATA_CONFLICT"); }
        finally { reset(seats); }
        assertThat(count(r)).as("Flushed first insert must roll back with whole bulk transaction").isZero();
    }
    @Test void concurrentBulkCreatesOnlyOneLayout() throws Exception {
        long r=room();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task=()->{ start.await();return admin.call("POST",layout(r)+"/bulk",bulk(List.of("A","B"),3),true).statusCode(); };
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            assertThat(count(r)).isEqualTo(6);
        } finally { pool.shutdownNow(); }
    }
    long history(long room,long seat,boolean future) {
        if(historyUser==0) {
            String email="history-"+suffix+"@example.invalid";
            jdbc.update("INSERT INTO users(username,email,display_name,status) VALUES(?,?,?,'INACTIVE')","history-"+suffix,email,"Non-login history fixture");
            historyUser=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
        }
        LocalDateTime start=LocalDateTime.now().plusDays(future?7:-7).withSecond(0).withNano(0);
        jdbc.update("INSERT INTO showtimes(movie_id,room_id,duration_minutes_snapshot,minimum_age_snapshot,starts_at,ends_at,standard_price,vip_price) VALUES(1,?,120,13,?,?,80000,100000)",room,start,start.plusMinutes(120));
        long show=jdbc.queryForObject("SELECT id FROM showtimes WHERE room_id=?",Long.class,room);
        String code=prefix+"-B"+(++serial);
        jdbc.update("INSERT INTO bookings(code,showtime_id,room_id,created_by,created_by_name_snapshot,movie_title_snapshot,room_code_snapshot,price_version_snapshot,total_amount,age_checked) VALUES(?,?,?,?, 'History fixture','Demo','Fixture',0,80000,1)",code,show,room,historyUser);
        long booking=jdbc.queryForObject("SELECT id FROM bookings WHERE code=?",Long.class,code);
        historicalRooms.add(room);
        jdbc.update("INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price) VALUES(?,?,?,?,'A1','STANDARD',80000)",booking,show,room,seat);
        return booking;
    }
    @Test void futureBookingsBlockStatusAndStructuralChanges() throws Exception {
        long r=room(),s=seat(r,"A",1),booking=history(r,s,true);
        error(admin.call("PUT",path(r),Map.of("code",prefix+"-UPDATED","name","Updated room","status","INACTIVE"),true),409,"ROOM_HAS_UNFINISHED_SHOWTIMES");
        error(admin.call("PUT",layout(r)+"/"+s,seat("A",1,"STANDARD","INACTIVE"),true),409,"SEAT_STATUS_LOCKED");
        for(var change:List.of(seat("B",1,"STANDARD","ACTIVE"),seat("A",1,"VIP","ACTIVE")))
            error(admin.call("PUT",layout(r)+"/"+s,change,true),409,"SEAT_LAYOUT_LOCKED");
        error(admin.call("POST",layout(r),seat("A",2,"STANDARD","ACTIVE"),true),409,"SEAT_LAYOUT_LOCKED");
        error(admin.call("DELETE",layout(r)+"/"+s,null,true),409,"SEAT_LAYOUT_LOCKED");
        error(admin.call("DELETE",path(r),null,true),409,"ROOM_HAS_SHOWTIMES");
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,booking)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT unit_price FROM booking_seats WHERE booking_id=?",java.math.BigDecimal.class,booking)).isEqualByComparingTo("80000");
    }
    @Test void pastBookingsKeepPriceHistoryWhilePhysicalSeatCanStop() throws Exception {
        long r=room(),s=seat(r,"A",1),booking=history(r,s,false);
        body(admin.call("PUT",layout(r)+"/"+s,seat("A",1,"STANDARD","INACTIVE"),true),200);
        error(admin.call("PUT",layout(r)+"/"+s,seat("A",1,"VIP","INACTIVE"),true),409,"SEAT_LAYOUT_LOCKED");
        error(admin.call("DELETE",path(r),null,true),409,"ROOM_HAS_SHOWTIMES");
        assertThat(jdbc.queryForObject("SELECT unit_price FROM booking_seats WHERE booking_id=?",java.math.BigDecimal.class,booking)).isEqualByComparingTo("80000");
        assertThat(jdbc.queryForObject("SELECT seat_type_snapshot FROM booking_seats WHERE booking_id=?",String.class,booking)).isEqualTo("STANDARD");
    }
    @Test void adminOnlyAndCsrfStillRequired() throws Exception {
        long r=room(),s=seat(r,"A",1);Browser anonymous=new Browser();
        for(String path:List.of("/api/admin/rooms",layout(r))) {
            error(anonymous.call("GET",path,null,false),401,"UNAUTHENTICATED");
            error(staff.call("GET",path,null,false),403,"FORBIDDEN");
            error(staff.call("POST",path,Map.of(),true),403,"FORBIDDEN");
            error(admin.call("POST",path,Map.of(),false),403,"CSRF_INVALID");
        }
        error(staff.call("PUT",layout(r)+"/"+s,seat("A",1,"VIP","ACTIVE"),true),403,"FORBIDDEN");
        error(staff.call("DELETE",layout(r)+"/"+s,null,true),403,"FORBIDDEN");
        error(staff.call("POST",layout(r)+"/bulk",bulk(List.of("A"),2),true),403,"FORBIDDEN");
    }
}
