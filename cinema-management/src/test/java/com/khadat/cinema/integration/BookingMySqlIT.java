package com.khadat.cinema.integration;
import com.fasterxml.jackson.databind.*;
import com.khadat.cinema.repository.BookingSeatRepository;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="CINEMA_MYSQL_IT",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ShowtimeMySqlIT.ConnectionObservation.class)
class BookingMySqlIT {
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired PasswordEncoder encoder;
    @MockitoSpyBean BookingSeatRepository details;@LocalServerPort int port;
    final String suffix=UUID.randomUUID().toString().replace("-","").substring(0,16),prefix="BK-"+suffix;
    final String adminEmail="bk-admin-"+suffix+"@example.invalid",staffEmail="bk-staff-"+suffix+"@example.invalid";
    final String password=secret();Browser admin,staff,staff2;long adminId,staffId;
    final List<Long> ownRooms=new ArrayList<>(),ownMovies=new ArrayList<>();final List<Map<String,Object>> evidence=new ArrayList<>();
    long room,movie,show;List<Long> seatIds;int serial;
    static String secret(){byte[] b=new byte[24];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    @BeforeAll void accounts()throws Exception{
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).startsWith("cinema_management_test_");
        for(String email:List.of(adminEmail,staffEmail)) {
            jdbc.update("INSERT INTO users(username,email,display_name,password_hash,status) VALUES(?,?,?,?, 'ACTIVE')",email.substring(0,email.indexOf('@')),email,"Booking test actor",encoder.encode(password));
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u JOIN roles r ON r.code=? WHERE u.email=?",email.equals(adminEmail)?"ADMIN":"STAFF",email);
        }
        adminId=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,adminEmail);staffId=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,staffEmail);
        admin=login(adminEmail);staff=login(staffEmail);staff2=login(staffEmail);
    }
    @BeforeEach void fixture(){
        String tag=prefix+"-"+(++serial);jdbc.update("INSERT INTO movies(code,title,duration_minutes,minimum_age,status) VALUES(?,?,120,13,'ACTIVE')",tag,tag);
        movie=jdbc.queryForObject("SELECT id FROM movies WHERE code=?",Long.class,tag);ownMovies.add(movie);jdbc.update("INSERT INTO movie_genres(movie_id,genre_id) VALUES(?,1)",movie);
        jdbc.update("INSERT INTO rooms(code,name,status) VALUES(?,?,'ACTIVE')",tag,tag);room=jdbc.queryForObject("SELECT id FROM rooms WHERE code=?",Long.class,tag);ownRooms.add(room);
        for(int i=1;i<=4;i++)jdbc.update("INSERT INTO seats(room_id,code,row_label,seat_number,seat_type,status) VALUES(?,?,'A',?,?,?)",room,"A"+i,i,i==2?"VIP":"STANDARD",i==4?"INACTIVE":"ACTIVE");
        seatIds=jdbc.queryForList("SELECT id FROM seats WHERE room_id=? ORDER BY id",Long.class,room);
        LocalDateTime start=LocalDate.now(ZoneOffset.ofHours(7)).plusDays(10).atTime(18,0);
        jdbc.update("INSERT INTO showtimes(movie_id,room_id,duration_minutes_snapshot,minimum_age_snapshot,starts_at,ends_at,standard_price,vip_price) VALUES(?,?,120,13,?,?,80000,100000)",movie,room,start,start.plusHours(2));
        show=jdbc.queryForObject("SELECT id FROM showtimes WHERE room_id=?",Long.class,room);
    }
    @AfterEach void cleanup(){
        ShowtimeMySqlIT.Gate.barrier=null;reset(details);
        for(long r:ownRooms) if(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE room_id=?",Long.class,r)==0){jdbc.update("DELETE FROM showtimes WHERE room_id=?",r);jdbc.update("DELETE FROM seats WHERE room_id=?",r);jdbc.update("DELETE FROM rooms WHERE id=?",r);}
        ownRooms.removeIf(r->jdbc.queryForObject("SELECT COUNT(*) FROM rooms WHERE id=?",Long.class,r)==0);
        for(long m:ownMovies)if(jdbc.queryForObject("SELECT COUNT(*) FROM showtimes WHERE movie_id=?",Long.class,m)==0){jdbc.update("DELETE FROM movie_genres WHERE movie_id=?",m);jdbc.update("DELETE FROM movies WHERE id=?",m);}
        ownMovies.removeIf(m->jdbc.queryForObject("SELECT COUNT(*) FROM movies WHERE id=?",Long.class,m)==0);
    }
    @AfterAll void finish()throws Exception{
        for(String email:List.of(adminEmail,staffEmail)){
            jdbc.update("DELETE ur FROM user_roles ur JOIN users u ON u.id=ur.user_id WHERE u.email=?",email);
            // Keep users referenced by history, but make the ephemeral login permanently unusable.
            jdbc.update("UPDATE users SET status='INACTIVE',password_hash=NULL WHERE email=?",email);
        }
        Files.writeString(Path.of("target/booking-concurrency.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }
    class Browser {
        final CookieManager jar=new CookieManager(null,CookiePolicy.ACCEPT_ALL);final HttpClient http=HttpClient.newBuilder().cookieHandler(jar).followRedirects(HttpClient.Redirect.NEVER).build();String csrf;
        HttpResponse<String> call(String method,String path,Object body,String key,boolean token)throws Exception{
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Accept","application/json");if(token&&csrf!=null)b.header("X-CSRF-TOKEN",csrf);
            if(key!=null)b.header("Idempotency-Key",key);if(body!=null)b.header("Content-Type","application/json");
            return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        void token()throws Exception{csrf=body(call("GET","/api/auth/csrf",null,null,false),200).get("token").asText();}
    }
    Browser login(String email)throws Exception{Browser b=new Browser();b.token();body(b.call("POST","/api/auth/login",Map.of("email",email,"password",password),null,true),200);b.token();return b;}
    JsonNode body(HttpResponse<String> r,int status)throws Exception{assertThat(r.statusCode()).as("HTTP %s: %s",status,r.body()).isEqualTo(status);return json.readTree(r.body());}
    JsonNode error(HttpResponse<String> r,int status,String code)throws Exception{var b=body(r,status);assertThat(b.get("code").asText()).isEqualTo(code);assertThat(b.get("fieldErrors").isObject()).isTrue();assertThat(r.body().contains(password)||b.has("trace")||b.has("exception")).isFalse();return b;}
    Map<String,Object> request(List<Long> selected){return new LinkedHashMap<>(Map.of("showtimeId",show,"seatIds",selected,"priceVersion",0,"ageChecked",true));}
    String key(){return UUID.randomUUID().toString();}
    JsonNode book(Browser b,List<Long> selected,String key)throws Exception{return body(b.call("POST","/api/bookings",request(selected),key,true),201);}
    String cancel(long id){return "/api/bookings/"+id+"/cancel";}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE showtime_id=?",Long.class,show);}
    JsonNode map()throws Exception{return body(staff.call("GET","/api/showtimes/"+show+"/seats",null,null,false),200);}
    @Test void mapAndServerPricesActorAndHistory()throws Exception{
        JsonNode before=map();assertThat(before.get("seats").size()).isEqualTo(4);assertThat(before.get("seats").get(3).get("availability").asText()).isEqualTo("UNAVAILABLE");
        var payload=request(seatIds.subList(0,2));payload.put("createdBy",adminId);payload.put("totalAmount",1);payload.put("unitPrice",1);
        JsonNode b=body(staff.call("POST","/api/bookings",payload,key(),true),201);
        assertThat(b.get("createdBy").asLong()).isEqualTo(staffId);assertThat(b.get("totalAmount").decimalValue()).isEqualByComparingTo("180000");
        assertThat(b.get("seats").get(1).get("unitPrice").decimalValue()).isEqualByComparingTo("100000");
        assertThat(map().get("seats").get(0).get("availability").asText()).isEqualTo("BOOKED");
        assertThat(map().toString()).doesNotContain("createdBy","createdByName","password","idempotency");
        LocalDateTime next=jdbc.queryForObject("SELECT starts_at FROM showtimes WHERE id=?",LocalDateTime.class,show).plusHours(3);
        jdbc.update("INSERT INTO showtimes(movie_id,room_id,duration_minutes_snapshot,minimum_age_snapshot,starts_at,ends_at,standard_price,vip_price) VALUES(?,?,120,13,?,?,80000,100000)",movie,room,next,next.plusHours(2));
        long otherShow=jdbc.queryForObject("SELECT MAX(id) FROM showtimes WHERE room_id=?",Long.class,room);
        JsonNode otherMap=body(staff.call("GET","/api/showtimes/"+otherShow+"/seats",null,null,false),200);
        assertThat(otherMap.get("seats").get(0).get("availability").asText()).isEqualTo("AVAILABLE");
        var otherRequest=request(List.of(seatIds.get(0)));otherRequest.put("showtimeId",otherShow);
        body(staff.call("POST","/api/bookings",otherRequest,key(),true),201);
        body(admin.call("GET","/api/bookings/by-code/"+b.get("code").asText(),null,null,false),200);
        JsonNode list=body(staff.call("GET","/api/bookings?showtimeId="+show+"&q="+b.get("code").asText()+"&size=1&sort=code",null,null,false),200);assertThat(list.get("total").asInt()).isEqualTo(1);
    }
    @Test void validationAndNoPartialData()throws Exception{
        for(var selected:List.of(List.<Long>of(),List.of(seatIds.get(0),seatIds.get(0)),List.of(seatIds.get(0),Long.MAX_VALUE))){error(staff.call("POST","/api/bookings",request(selected),key(),true),400,"VALIDATION_FAILED");assertThat(count("bookings")).isZero();}
        error(staff.call("POST","/api/bookings",request(List.of(seatIds.get(3))),key(),true),409,"SEAT_UNAVAILABLE");
        long other=jdbc.queryForObject("SELECT id FROM seats WHERE room_id=1 ORDER BY id LIMIT 1",Long.class);
        error(staff.call("POST","/api/bookings",request(List.of(seatIds.get(0),other)),key(),true),400,"VALIDATION_FAILED");
        var p=request(List.of(seatIds.get(0)));p.put("ageChecked",false);error(staff.call("POST","/api/bookings",p,key(),true),400,"VALIDATION_FAILED");
        error(staff.call("POST","/api/bookings",request(List.of(seatIds.get(0))),null,true),400,"VALIDATION_FAILED");assertThat(count("bookings")).isZero();
    }
    @Test void closedInactiveAndStalePriceBlocked()throws Exception{
        var p=request(List.of(seatIds.get(0)));p.put("priceVersion",99);error(staff.call("POST","/api/bookings",p,key(),true),409,"PRICE_CHANGED");
        jdbc.update("UPDATE rooms SET status='INACTIVE' WHERE id=?",room);error(staff.call("POST","/api/bookings",request(List.of(seatIds.get(0))),key(),true),409,"RESOURCE_INACTIVE");jdbc.update("UPDATE rooms SET status='ACTIVE' WHERE id=?",room);
        LocalDateTime past=LocalDateTime.now().minusDays(2).withSecond(0).withNano(0);jdbc.update("UPDATE showtimes SET starts_at=?,ends_at=? WHERE id=?",past,past.plusHours(2),show);
        error(staff.call("POST","/api/bookings",request(List.of(seatIds.get(0))),key(),true),409,"SHOWTIME_NOT_BOOKABLE");assertThat(map().get("bookable").asBoolean()).isFalse();
    }
    @Test void idempotencyReplayChangedContentAndCreatorScope()throws Exception{
        String key=key();JsonNode first=book(staff,seatIds.subList(0,2),key);
        var replay=staff.call("POST","/api/bookings",request(List.of(seatIds.get(1),seatIds.get(0))),key,true);JsonNode second=body(replay,200);
        assertThat(second.get("id")).isEqualTo(first.get("id"));assertThat(replay.headers().firstValue("Idempotency-Replayed")).hasValue("true");assertThat(count("bookings")).isEqualTo(1);
        error(staff.call("POST","/api/bookings",request(List.of(seatIds.get(2))),key,true),409,"IDEMPOTENCY_KEY_REUSED");book(admin,List.of(seatIds.get(2)),key);assertThat(count("bookings")).isEqualTo(2);
        assertThatThrownBy(()->jdbc.update("UPDATE bookings SET request_hash=REPEAT('a',64) WHERE id=?",first.get("id").asLong())).isInstanceOf(DataAccessException.class);
    }
    @Test void cancellationReleaseRebookRepeatAndReplayOldRequest()throws Exception{
        String oldKey=key();JsonNode first=book(staff,List.of(seatIds.get(0)),oldKey);long old=first.get("id").asLong();
        error(staff.call("POST",cancel(old),Map.of("reason","Own order"),null,true),403,"FORBIDDEN");
        JsonNode cancelled=body(admin.call("POST",cancel(old),Map.of("reason","Original cancellation"),null,true),200);assertThat(cancelled.get("seats").get(0).get("bookingStatus").asText()).isEqualTo("CANCELLED");
        assertThat(map().get("seats").get(0).get("availability").asText()).isEqualTo("AVAILABLE");JsonNode replacement=book(staff,List.of(seatIds.get(0)),key());
        JsonNode again=body(admin.call("POST",cancel(old),Map.of("reason","Do not rewrite"),null,true),200);assertThat(again.get("cancelReason")).isEqualTo(cancelled.get("cancelReason"));assertThat(again.get("cancelledAt")).isEqualTo(cancelled.get("cancelledAt"));
        assertThat(body(staff.call("POST","/api/bookings",request(List.of(seatIds.get(0))),oldKey,true),200).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(body(staff.call("GET","/api/bookings/"+replacement.get("id").asLong(),null,null,false),200).get("status").asText()).isEqualTo("CONFIRMED");assertThat(count("bookings")).isEqualTo(2);
        LocalDateTime past=LocalDateTime.now().minusDays(2).withSecond(0).withNano(0);
        jdbc.update("UPDATE showtimes SET starts_at=?,ends_at=? WHERE id=?",past,past.plusHours(2),show);
        error(admin.call("POST",cancel(replacement.get("id").asLong()),Map.of("reason","Too late"),null,true),409,"SHOWTIME_NOT_BOOKABLE");
        assertThat(body(admin.call("POST",cancel(old),Map.of("reason","Replay after cutoff"),null,true),200).get("cancelledAt")).isEqualTo(cancelled.get("cancelledAt"));
    }
    @Test void historicalPricesDoNotFollowDatabaseCatalogChanges()throws Exception{
        JsonNode b=book(staff,List.of(seatIds.get(1)),key());
        // Simulate later catalog change directly in isolated fixture; production APIs remain stricter.
        jdbc.update("UPDATE showtimes SET vip_price=150000,price_version=price_version+1 WHERE id=?",show);jdbc.update("UPDATE seats SET seat_type='STANDARD' WHERE id=?",seatIds.get(1));
        JsonNode after=body(staff.call("GET","/api/bookings/"+b.get("id").asLong(),null,null,false),200);
        assertThat(after.get("totalAmount").decimalValue()).isEqualByComparingTo("100000");assertThat(after.get("seats").get(0).get("seatType").asText()).isEqualTo("VIP");
    }
    @Test void rollbackAfterActualWritesAndDatabaseOccupancyConstraint()throws Exception{
        doAnswer(call->{assertThat(count("bookings")).isEqualTo(1);assertThat(count("booking_seats")).isEqualTo(2);throw new DataIntegrityViolationException("Test-only fault after inserts");}).when(details).flush();
        try{error(staff.call("POST","/api/bookings",request(seatIds.subList(0,2)),key(),true),409,"BOOKING_CONFLICT");}finally{reset(details);}
        assertThat(count("bookings")).isZero();assertThat(count("booking_seats")).isZero();
        JsonNode a=book(staff,List.of(seatIds.get(0)),key()),b=book(staff,List.of(seatIds.get(1)),key());
        assertThatThrownBy(()->jdbc.update("INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price) VALUES(?,?,?,?,'A1','STANDARD',80000)",b.get("id").asLong(),show,room,seatIds.get(0))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("booking_seats")).isEqualTo(2);
    }
    @Test void permissionsCsrfAndMissingResources()throws Exception{
        Browser anon=new Browser();error(anon.call("GET","/api/bookings",null,null,false),401,"UNAUTHENTICATED");
        error(staff.call("POST","/api/bookings",request(List.of(seatIds.get(0))),key(),false),403,"CSRF_INVALID");
        error(admin.call("GET","/api/bookings/"+Long.MAX_VALUE,null,null,false),404,"RESOURCE_NOT_FOUND");
        error(admin.call("GET","/api/bookings/by-code/MISSING",null,null,false),404,"RESOURCE_NOT_FOUND");
        error(staff.call("GET","/api/bookings?size=101",null,null,false),400,"VALIDATION_FAILED");
        JsonNode b=book(admin,List.of(seatIds.get(0)),key());error(admin.call("DELETE","/api/bookings/"+b.get("id").asLong(),null,null,true),403,"FORBIDDEN");
    }
    List<HttpResponse<String>> race(String label,Callable<HttpResponse<String>> a,Callable<HttpResponse<String>> b)throws Exception{
        var gate=ShowtimeMySqlIT.Gate.class;ShowtimeMySqlIT.Gate.ids.clear();ShowtimeMySqlIT.Gate.tickets.set(0);ShowtimeMySqlIT.Gate.barrier=new CyclicBarrier(2);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);List<HttpResponse<String>> result;
        try{var x=pool.submit(()->{start.await();return a.call();});var y=pool.submit(()->{start.await();return b.call();});start.countDown();result=List.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));}finally{ShowtimeMySqlIT.Gate.barrier=null;pool.shutdownNow();}
        assertThat(ShowtimeMySqlIT.Gate.ids.size()).isEqualTo(2);
        long duplicate=jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT seat_id FROM booking_seats WHERE showtime_id=? AND booking_status='CONFIRMED' GROUP BY seat_id HAVING COUNT(*)>1) x",Long.class,show);assertThat(duplicate).isZero();
        long partial=jdbc.queryForObject("SELECT COUNT(*) FROM bookings b WHERE b.showtime_id=? AND (NOT EXISTS(SELECT 1 FROM booking_seats d WHERE d.booking_id=b.id) OR b.total_amount<>(SELECT COALESCE(SUM(d.unit_price),0) FROM booking_seats d WHERE d.booking_id=b.id))",Long.class,show);assertThat(partial).isZero();
        var proof=Map.<String,Object>of("case",label,"mysqlConnectionIds",new TreeSet<>(ShowtimeMySqlIT.Gate.ids),"statuses",result.stream().map(HttpResponse::statusCode).toList(),"duplicateActiveSeats",duplicate,"partialBookings",partial);
        evidence.add(proof);System.out.println("BOOKING_RACE "+proof);return result;
    }
    @Test void simultaneousDifferentRequestsSameSeat()throws Exception{
        var responses=race("same-seat",()->staff.call("POST","/api/bookings",request(List.of(seatIds.get(0))),key(),true),()->admin.call("POST","/api/bookings",request(List.of(seatIds.get(0))),key(),true));
        assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(201,409);assertThat(count("bookings")).isEqualTo(1);
    }
    @Test void simultaneousOverlappingGroupsAreAllOrNothing()throws Exception{
        var responses=race("overlapping-groups",()->staff.call("POST","/api/bookings",request(seatIds.subList(0,2)),key(),true),()->admin.call("POST","/api/bookings",request(seatIds.subList(1,3)),key(),true));
        assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(201,409);assertThat(count("bookings")).isEqualTo(1);assertThat(count("booking_seats")).isEqualTo(2);
    }
    @Test void simultaneousSameKeyCreatesOneBooking()throws Exception{
        String k=key();var p=request(seatIds.subList(0,2));var responses=race("same-idempotency-key",()->staff.call("POST","/api/bookings",p,k,true),()->staff2.call("POST","/api/bookings",p,k,true));
        assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(201,200);assertThat(count("bookings")).isEqualTo(1);assertThat(json.readTree(responses.get(0).body()).get("id")).isEqualTo(json.readTree(responses.get(1).body()).get("id"));
    }
    @Test void simultaneousSameKeyDifferentShowsUsesDatabaseUnique()throws Exception{
        String k=key();var first=request(List.of(seatIds.get(0)));fixture();var second=request(List.of(seatIds.get(0)));
        var responses=race("same-key-different-shows",()->staff.call("POST","/api/bookings",first,k,true),()->staff2.call("POST","/api/bookings",second,k,true));
        assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(201,409);
        for(var response:responses)if(response.statusCode()==409)error(response,409,"IDEMPOTENCY_KEY_REUSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE created_by=? AND idempotency_key=?",Long.class,staffId,k)).isEqualTo(1);
    }
    @Test void simultaneousCancelAndRebook()throws Exception{
        long id=book(staff,List.of(seatIds.get(0)),key()).get("id").asLong();String k=key();
        var responses=race("cancel-and-rebook",()->admin.call("POST",cancel(id),Map.of("reason","Race cancellation"),null,true),()->staff.call("POST","/api/bookings",request(List.of(seatIds.get(0))),k,true));
        assertThat(responses.get(0).statusCode()).isEqualTo(200);assertThat(responses.get(1).statusCode()).isIn(201,409);
        if(responses.get(1).statusCode()==409)book(staff,List.of(seatIds.get(0)),k);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_seats WHERE showtime_id=? AND booking_status='CONFIRMED'",Long.class,show)).isEqualTo(1);
    }
    @Test void simultaneousShowtimeCancelAndBooking()throws Exception{
        var responses=race("showtime-cancel-vs-book",()->admin.call("POST","/api/admin/showtimes/"+show+"/cancel",Map.of("reason","Race show cancellation"),null,true),()->staff.call("POST","/api/bookings",request(List.of(seatIds.get(0))),key(),true));
        assertThat(responses.stream().map(HttpResponse::statusCode)).containsAnyOf(200,201);assertThat(responses.stream().filter(r->r.statusCode()==409).count()).isEqualTo(1);
        String status=jdbc.queryForObject("SELECT status FROM showtimes WHERE id=?",String.class,show);if(status.equals("CANCELLED"))assertThat(count("bookings")).isZero();else assertThat(count("bookings")).isEqualTo(1);
    }
    @Test void simultaneousShowtimeEditAndBookingIsSerialized()throws Exception{
        LocalDateTime changed=jdbc.queryForObject("SELECT starts_at FROM showtimes WHERE id=?",LocalDateTime.class,show).plusHours(1);
        var update=Map.of("movieId",movie,"roomId",room,"startsAt",changed.atOffset(ZoneOffset.ofHours(7)).toString(),"standardPrice",80000,"vipPrice",100000);
        var responses=race("showtime-edit-vs-book",()->admin.call("PUT","/api/admin/showtimes/"+show,update,null,true),()->staff.call("POST","/api/bookings",request(List.of(seatIds.get(0))),key(),true));
        assertThat(responses.get(0).statusCode()).isIn(200,409);assertThat(responses.get(1).statusCode()).isEqualTo(201);
        LocalDateTime actual=jdbc.queryForObject("SELECT starts_at FROM showtimes WHERE id=?",LocalDateTime.class,show);
        assertThat(OffsetDateTime.parse(json.readTree(responses.get(1).body()).get("startsAt").asText()).toLocalDateTime()).isEqualTo(actual);
        assertThat(count("bookings")).isEqualTo(1);
    }
}
