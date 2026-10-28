package com.khadat.cinema.integration;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="CINEMA_MYSQL_IT",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ShowtimeMySqlIT.ConnectionObservation.class)
class ShowtimeMySqlIT {
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired PasswordEncoder encoder;@LocalServerPort int port;
    final String suffix=UUID.randomUUID().toString().replace("-","").substring(0,16),prefix="SH-"+suffix;
    final String adminEmail="show-admin-"+suffix+"@example.invalid",staffEmail="show-staff-"+suffix+"@example.invalid";
    final String password=secret();int serial;Browser admin,admin2,staff;
    final List<Long> rooms=new ArrayList<>(),movies=new ArrayList<>();final Set<Long> keptRooms=new HashSet<>(),keptMovies=new HashSet<>();
    final List<Map<String,Object>> evidence=new ArrayList<>();
    static String secret(){byte[] b=new byte[24];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    @BeforeAll void setup() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).startsWith("cinema_management_test_");
        for(String email:List.of(adminEmail,staffEmail)) {
            jdbc.update("INSERT INTO users(username,email,display_name,password_hash,status) VALUES(?,?,?,?, 'ACTIVE')",email.substring(0,email.indexOf('@')),email,"Showtime test",encoder.encode(password));
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u JOIN roles r ON r.code=? WHERE u.email=?",email.equals(adminEmail)?"ADMIN":"STAFF",email);
        }
        admin=login(adminEmail);admin2=login(adminEmail);staff=login(staffEmail);
    }
    @AfterEach void cleanupFixtures() {
        Gate.barrier=null;
        for(long r:rooms) if(!keptRooms.contains(r)) {jdbc.update("DELETE FROM showtimes WHERE room_id=?",r);jdbc.update("DELETE FROM seats WHERE room_id=?",r);jdbc.update("DELETE FROM rooms WHERE id=?",r);}
        rooms.removeIf(r->!keptRooms.contains(r));
        for(long m:movies) if(!keptMovies.contains(m)) {jdbc.update("DELETE FROM movie_genres WHERE movie_id=?",m);jdbc.update("DELETE FROM movies WHERE id=?",m);}
        movies.removeIf(m->!keptMovies.contains(m));
    }
    @AfterAll void finish() throws Exception {
        for(String email:List.of(adminEmail,staffEmail)) {jdbc.update("DELETE ur FROM user_roles ur JOIN users u ON u.id=ur.user_id WHERE u.email=?",email);jdbc.update("DELETE FROM users WHERE email=?",email);}
        Files.writeString(Path.of("target/showtime-concurrency.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }
    class Browser {
        final CookieManager jar=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(jar).followRedirects(HttpClient.Redirect.NEVER).build();String csrf;
        HttpResponse<String> call(String method,String path,Object body,boolean token) throws Exception {
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Accept","application/json");
            if(token&&csrf!=null)b.header("X-CSRF-TOKEN",csrf);if(body!=null)b.header("Content-Type","application/json");
            return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        void token() throws Exception{csrf=body(call("GET","/api/auth/csrf",null,false),200).get("token").asText();}
    }
    Browser login(String email)throws Exception{Browser b=new Browser();b.token();body(b.call("POST","/api/auth/login",Map.of("email",email,"password",password),true),200);b.token();return b;}
    JsonNode body(HttpResponse<String> r,int status)throws Exception{assertThat(r.statusCode()).as("HTTP %s: %s",status,r.body()).isEqualTo(status);return json.readTree(r.body());}
    JsonNode error(HttpResponse<String> r,int status,String code)throws Exception{JsonNode b=body(r,status);assertThat(b.get("code").asText()).isEqualTo(code);assertThat(b.get("fieldErrors").isObject()).isTrue();assertThat(b.has("trace")||b.has("exception")||r.body().contains(password)).isFalse();return b;}
    long movie() {
        String code=prefix+"-M"+(++serial);
        jdbc.update("INSERT INTO movies(code,title,duration_minutes,minimum_age,status) VALUES(?,?,120,13,'ACTIVE')",code,code);
        long id=jdbc.queryForObject("SELECT id FROM movies WHERE code=?",Long.class,code);movies.add(id);
        jdbc.update("INSERT INTO movie_genres(movie_id,genre_id) VALUES(?,1)",id);return id;
    }
    long room() {
        String code=prefix+"-R"+(++serial);jdbc.update("INSERT INTO rooms(code,name,status) VALUES(?,?,'ACTIVE')",code,code);
        long id=jdbc.queryForObject("SELECT id FROM rooms WHERE code=?",Long.class,code);rooms.add(id);
        jdbc.update("INSERT INTO seats(room_id,code,row_label,seat_number,seat_type,status) VALUES(?,'A1','A',1,'STANDARD','ACTIVE')",id);return id;
    }
    OffsetDateTime start(int hour){return LocalDate.now(ZoneOffset.ofHours(7)).plusDays(20).atTime(hour,0).atOffset(ZoneOffset.ofHours(7));}
    Map<String,Object> request(long m,long r,OffsetDateTime t){return new LinkedHashMap<>(Map.of("movieId",m,"roomId",r,"startsAt",t.toString(),"standardPrice",80000,"vipPrice",100000));}
    long create(long m,long r,OffsetDateTime t)throws Exception{return body(admin.call("POST","/api/admin/showtimes",request(m,r,t),true),201).get("id").asLong();}
    String path(long id){return "/api/admin/showtimes/"+id;}
    @Test void validCreateSnapshotAndHateoasLinks()throws Exception{
        long m=movie(),r=room(),id=create(m,r,start(10));JsonNode detail=body(admin.call("GET",path(id),null,false),200);
        assertThat(OffsetDateTime.parse(detail.get("endsAt").asText())).isEqualTo(start(12));assertThat(detail.get("durationMinutes").asInt()).isEqualTo(120);
        for(String rel:List.of("self","collection","movie","room")) body(admin.call("GET",detail.get("_links").get(rel).get("href").asText(),null,false),200);
        assertThat(detail.get("_links").has("update")&&detail.get("_links").has("cancel")).isTrue();
        JsonNode publicRead=body(staff.call("GET","/api/showtimes/"+id,null,false),200);
        assertThat(publicRead.get("_links").size()).isEqualTo(2);
        body(staff.call("GET",publicRead.get("_links").get("collection").get("href").asText(),null,false),200);
    }
    @Test void invalidReferencesActiveStateAndSeats()throws Exception{
        long m=movie(),r=room();error(admin.call("POST","/api/admin/showtimes",request(Long.MAX_VALUE,r,start(10)),true),404,"RESOURCE_NOT_FOUND");
        error(admin.call("POST","/api/admin/showtimes",request(m,Long.MAX_VALUE,start(10)),true),404,"RESOURCE_NOT_FOUND");
        jdbc.update("UPDATE movies SET status='INACTIVE' WHERE id=?",m);error(admin.call("POST","/api/admin/showtimes",request(m,r,start(10)),true),409,"MOVIE_INACTIVE");
        jdbc.update("UPDATE movies SET status='ACTIVE' WHERE id=?",m);jdbc.update("UPDATE rooms SET status='INACTIVE' WHERE id=?",r);
        error(admin.call("POST","/api/admin/showtimes",request(m,r,start(10)),true),409,"ROOM_INACTIVE");
        jdbc.update("UPDATE rooms SET status='ACTIVE' WHERE id=?",r);jdbc.update("UPDATE seats SET status='INACTIVE' WHERE room_id=?",r);
        error(admin.call("POST","/api/admin/showtimes",request(m,r,start(10)),true),409,"ROOM_NO_ACTIVE_SEATS");
    }
    @Test void invalidTimeMoneyAndDerivedInputs()throws Exception{
        long m=movie(),r=room();
        for(var bad:List.<Map.Entry<String,Object>>of(Map.entry("startsAt",start(10).withSecond(1).toString()),Map.entry("startsAt",start(10).withOffsetSameInstant(ZoneOffset.UTC).toString()),
            Map.entry("startsAt",start(10).minusDays(25).toString()),Map.entry("standardPrice",-1),Map.entry("standardPrice",1.5),Map.entry("vipPrice",79999),Map.entry("vipPrice",1000000000000L),Map.entry("endsAt",start(11).toString()),Map.entry("status","CANCELLED"))) {
            var p=request(m,r,start(10));p.put(bad.getKey(),bad.getValue());error(admin.call("POST","/api/admin/showtimes",p,true),400,"VALIDATION_FAILED");
        }
        var p=request(m,r,start(10));p.put("startsAt","2026-11-01T10:00:00");error(admin.call("POST","/api/admin/showtimes",p,true),400,"INVALID_REQUEST");
    }
    @Test void allOverlapShapesExactBufferAndDifferentRoom()throws Exception{
        long m=movie(),r=room();create(m,r,start(10));
        for(OffsetDateTime t:List.of(start(9),start(10).plusMinutes(30),start(8).plusMinutes(30),start(12).plusMinutes(14),start(7).plusMinutes(46)))
            error(admin.call("POST","/api/admin/showtimes",request(m,r,t),true),409,"SHOWTIME_CONFLICT");
        long shortMovie=movie();jdbc.update("UPDATE movies SET duration_minutes=30 WHERE id=?",shortMovie);
        error(admin.call("POST","/api/admin/showtimes",request(shortMovie,r,start(10).plusMinutes(30)),true),409,"SHOWTIME_CONFLICT");
        long longMovie=movie();jdbc.update("UPDATE movies SET duration_minutes=300 WHERE id=?",longMovie);
        error(admin.call("POST","/api/admin/showtimes",request(longMovie,r,start(8)),true),409,"SHOWTIME_CONFLICT");
        create(m,r,start(12).plusMinutes(15));create(m,r,start(7).plusMinutes(45));create(m,room(),start(10));
    }
    @Test void midnightAndStartDateFilter()throws Exception{
        long m=movie(),r=room(),id=create(m,r,start(23));
        JsonNode d=body(admin.call("GET",path(id),null,false),200);assertThat(OffsetDateTime.parse(d.get("endsAt").asText())).isEqualTo(start(23).plusHours(2));
        JsonNode first=body(staff.call("GET","/api/showtimes?roomId="+r+"&date="+start(23).toLocalDate(),null,false),200);assertThat(first.get("total").asInt()).isEqualTo(1);
        JsonNode next=body(staff.call("GET","/api/showtimes?roomId="+r+"&date="+start(23).toLocalDate().plusDays(1),null,false),200);assertThat(next.get("total").asInt()).isZero();
        error(admin.call("POST","/api/admin/showtimes",request(m,r,start(23).plusHours(2).plusMinutes(14)),true),409,"SHOWTIME_CONFLICT");
        create(m,r,start(23).plusHours(2).plusMinutes(15));
    }
    @Test void updateSelfExcludeMoveRoomAndPriceVersion()throws Exception{
        long m=movie(),a=room(),b=room(),id=create(m,a,start(10));create(m,b,start(10));
        body(admin.call("PUT",path(id),request(m,a,start(10)),true),200);
        error(admin.call("PUT",path(id),request(m,b,start(10)),true),409,"SHOWTIME_CONFLICT");
        var p=request(m,b,start(14));p.put("vipPrice",110000);JsonNode changed=body(admin.call("PUT",path(id),p,true),200);
        assertThat(changed.get("roomId").asLong()).isEqualTo(b);assertThat(changed.get("priceVersion").asLong()).isEqualTo(1);
    }
    @Test void cancelledFreesSlotButCannotRestoreOrDelete()throws Exception{
        long m=movie(),r=room(),id=create(m,r,start(10));
        JsonNode cancelled=body(admin.call("POST",path(id)+"/cancel",Map.of("reason","Test cancel"),true),200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");assertThat(cancelled.get("_links").has("update")||cancelled.get("_links").has("cancel")||cancelled.get("_links").has("delete")).isFalse();
        create(m,r,start(10));error(admin.call("PUT",path(id),request(m,r,start(14)),true),409,"SHOWTIME_CANCELLED");
        error(admin.call("DELETE",path(id),null,true),409,"SHOWTIME_CANCELLED");
        long fresh=create(m,r,start(15));assertThat(admin.call("DELETE",path(fresh),null,true).statusCode()).isEqualTo(204);
    }
    @Test void pastReadOnlyAndMovieDurationFrozen()throws Exception{
        long m=movie(),r=room(),id=create(m,r,start(10));
        var moviePayload=new LinkedHashMap<String,Object>();moviePayload.put("code",jdbc.queryForObject("SELECT code FROM movies WHERE id=?",String.class,m));moviePayload.put("title","Title");moviePayload.put("durationMinutes",150);moviePayload.put("minimumAge",13);moviePayload.put("status","ACTIVE");moviePayload.put("genreIds",List.of(1));
        error(admin.call("PUT","/api/admin/movies/"+m,moviePayload,true),409,"MOVIE_SCHEDULE_FIELDS_LOCKED");
        assertThat(body(admin.call("GET",path(id),null,false),200).get("durationMinutes").asInt()).isEqualTo(120);
        LocalDateTime past=start(10).minusDays(25).toLocalDateTime();jdbc.update("UPDATE showtimes SET starts_at=?,ends_at=? WHERE id=?",past,past.plusMinutes(120),id);
        JsonNode d=body(admin.call("GET",path(id),null,false),200);assertThat(d.get("phase").asText()).isEqualTo("COMPLETED");assertThat(d.get("_links").has("update")).isFalse();
        error(admin.call("PUT",path(id),request(m,r,start(14)),true),409,"SHOWTIME_STARTED");
    }
    @Test void bookingHistoryBlocksEveryMutationAndRetainsPrice()throws Exception{
        long m=movie(),r=room(),id=create(m,r,start(10));String email="sh-history-"+suffix+"@example.invalid";
        jdbc.update("INSERT INTO users(username,email,display_name,status) VALUES(?,?,?,'INACTIVE')","sh-history-"+suffix,email,"No-login fixture");long user=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
        String code=prefix+"-BOOK";jdbc.update("INSERT INTO bookings(code,showtime_id,room_id,created_by,created_by_name_snapshot,movie_title_snapshot,room_code_snapshot,price_version_snapshot,total_amount,age_checked) VALUES(?,?,?,?, 'Fixture','Movie','Room',0,80000,1)",code,id,r,user);
        long booking=jdbc.queryForObject("SELECT id FROM bookings WHERE code=?",Long.class,code);keptRooms.add(r);keptMovies.add(m);
        long seat=jdbc.queryForObject("SELECT id FROM seats WHERE room_id=?",Long.class,r);
        jdbc.update("INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price) VALUES(?,?,?,?,'A1','STANDARD',80000)",booking,id,r,seat);
        for(boolean cancelled:List.of(false,true)) {
            error(admin.call("PUT",path(id),request(m,r,start(14)),true),409,"SHOWTIME_HAS_BOOKINGS");
            error(admin.call("POST",path(id)+"/cancel",Map.of("reason","Not allowed"),true),409,"SHOWTIME_HAS_BOOKINGS");
            error(admin.call("DELETE",path(id),null,true),409,"SHOWTIME_HAS_BOOKINGS");
            assertThat(body(admin.call("GET",path(id),null,false),200).get("_links").has("update")).isFalse();
            if(!cancelled)jdbc.update("UPDATE bookings SET status='CANCELLED',cancelled_at=NOW(6),cancelled_by=?,cancel_reason='Fixture cancel' WHERE id=?",user,booking);
        }
        assertThat(jdbc.queryForObject("SELECT unit_price FROM booking_seats WHERE booking_id=?",java.math.BigDecimal.class,booking)).isEqualByComparingTo("80000");
        assertThat(jdbc.queryForObject("SELECT status FROM showtimes WHERE id=?",String.class,id)).isEqualTo("SCHEDULED");
    }
    @Test void filterValidationPermissionsAndCsrf()throws Exception{
        long m=movie(),r=room(),id=create(m,r,start(10));
        JsonNode p=body(staff.call("GET","/api/showtimes?roomId="+r+"&movieId="+m+"&status=SCHEDULED&size=1",null,false),200);assertThat(p.get("total").asInt()).isEqualTo(1);
        String from=URLEncoder.encode(start(0).toString(),java.nio.charset.StandardCharsets.UTF_8);
        String to=URLEncoder.encode(start(0).plusDays(1).toString(),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body(staff.call("GET","/api/showtimes?roomId="+r+"&from="+from+"&to="+to,null,false),200).get("total").asInt()).isEqualTo(1);
        String distant=URLEncoder.encode(start(0).plusDays(32).toString(),java.nio.charset.StandardCharsets.UTF_8);
        error(staff.call("GET","/api/showtimes?from="+from+"&to="+distant,null,false),400,"VALIDATION_FAILED");
        for(String q:List.of("size=101","sort=movieTitle","status=COMPLETED","from=2026-11-01T00%3A00%3A00%2B07%3A00","date=2026-11-01&from=2026-11-01T00%3A00%3A00%2B07%3A00"))
            error(staff.call("GET","/api/showtimes?"+q,null,false),400,"VALIDATION_FAILED");
        Browser anon=new Browser();error(anon.call("GET","/api/showtimes",null,false),401,"UNAUTHENTICATED");
        error(staff.call("GET","/api/admin/showtimes",null,false),403,"FORBIDDEN");
        error(staff.call("POST","/api/admin/showtimes",request(m,r,start(14)),true),403,"FORBIDDEN");
        error(staff.call("POST",path(id)+"/cancel",Map.of("reason","No"),true),403,"FORBIDDEN");
        error(admin.call("POST","/api/admin/showtimes",request(m,r,start(14)),false),403,"CSRF_INVALID");
        error(admin.call("GET",path(Long.MAX_VALUE),null,false),404,"RESOURCE_NOT_FOUND");
    }
    void race(String label,Callable<HttpResponse<String>> a,Callable<HttpResponse<String>> b,int success,long room)throws Exception{
        Gate.ids.clear();Gate.tickets.set(0);Gate.barrier=new CyclicBarrier(2);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        List<Integer> statuses;
        try {var x=pool.submit(()->{start.await();return a.call().statusCode();});var y=pool.submit(()->{start.await();return b.call().statusCode();});start.countDown();statuses=List.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));}
        finally{Gate.barrier=null;pool.shutdownNow();}
        assertThat(statuses).containsExactlyInAnyOrder(success,409);assertThat(Gate.ids.size()).as("Distinct real MySQL connections held concurrently").isEqualTo(2);
        long conflicts=jdbc.queryForObject("SELECT COUNT(*) FROM showtimes a JOIN showtimes b ON a.room_id=b.room_id AND a.id<b.id AND a.status='SCHEDULED' AND b.status='SCHEDULED' AND a.starts_at<DATE_ADD(b.ends_at,INTERVAL 15 MINUTE) AND b.starts_at<DATE_ADD(a.ends_at,INTERVAL 15 MINUTE) WHERE a.room_id=?",Long.class,room);
        assertThat(conflicts).isZero();evidence.add(Map.of("case",label,"httpStatuses",statuses,"mysqlConnectionIds",new TreeSet<>(Gate.ids),"finalOverlapPairs",conflicts));
        System.out.println("SHOWTIME_RACE "+label+" statuses="+statuses+" mysqlConnectionIds="+Gate.ids+" finalOverlapPairs="+conflicts);
    }
    @Test void simultaneousCreateDifferentMoviesSameRoom()throws Exception{
        long a=movie(),b=movie(),r=room();race("create",()->admin.call("POST","/api/admin/showtimes",request(a,r,start(10)),true),()->admin2.call("POST","/api/admin/showtimes",request(b,r,start(10)),true),201,r);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM showtimes WHERE room_id=?",Long.class,r)).isEqualTo(1);
    }
    @Test void simultaneousMovesIntoSameDestination()throws Exception{
        long a=movie(),b=movie(),ra=room(),rb=room(),dest=room(),x=create(a,ra,start(10)),y=create(b,rb,start(10));
        race("transfer",()->admin.call("PUT",path(x),request(a,dest,start(10)),true),()->admin2.call("PUT",path(y),request(b,dest,start(10)),true),200,dest);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM showtimes WHERE room_id=?",Long.class,dest)).isEqualTo(1);
    }
    @Test void simultaneousEditsIntoSameSlot()throws Exception{
        long a=movie(),b=movie(),r=room(),x=create(a,r,start(8)),y=create(b,r,start(13));
        race("edit",()->admin.call("PUT",path(x),request(a,r,start(18)),true),()->admin2.call("PUT",path(y),request(b,r,start(18)),true),200,r);
    }
    static class Gate {static volatile CyclicBarrier barrier;static final AtomicInteger tickets=new AtomicInteger();static final Set<Long> ids=ConcurrentHashMap.newKeySet();}
    @TestConfiguration(proxyBeanMethods=false) static class ConnectionObservation {
        @Bean static BeanPostProcessor observeConnections(){return new BeanPostProcessor(){
            @Override public Object postProcessAfterInitialization(Object bean,String name){return bean instanceof DataSource ds && name.equals("dataSource")?new ObservedDataSource(ds):bean;}
        };}
    }
    static class ObservedDataSource extends DelegatingDataSource implements AutoCloseable {
        ObservedDataSource(DataSource ds){super(ds);}
        @Override public Connection getConnection()throws SQLException{return observe(super.getConnection());}
        @Override public Connection getConnection(String u,String p)throws SQLException{return observe(super.getConnection(u,p));}
        Connection observe(Connection c)throws SQLException{
            CyclicBarrier gate=Gate.barrier;
            if(gate!=null && Gate.tickets.getAndIncrement()<2)try {
                try(var s=c.createStatement();var rs=s.executeQuery("SELECT CONNECTION_ID()")){rs.next();Gate.ids.add(rs.getLong(1));}
                gate.await(15,TimeUnit.SECONDS);
            }catch(Exception ex){c.close();throw new SQLException("Test connection barrier failed",ex);}
            return c;
        }
        @Override public void close()throws Exception{if(getTargetDataSource() instanceof AutoCloseable closeable)closeable.close();}
    }
}
