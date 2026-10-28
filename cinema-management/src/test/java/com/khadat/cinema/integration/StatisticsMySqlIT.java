package com.khadat.cinema.integration;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="CINEMA_MYSQL_IT",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatisticsMySqlIT {
 @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired PasswordEncoder encoder;@LocalServerPort int port;
 final String key=UUID.randomUUID().toString().replace("-","").substring(0,12),prefix="ST-"+key,password=UUID.randomUUID().toString();
 final Map<String,Object> evidence=new LinkedHashMap<>();Browser admin,staff;long actor,a,b,r1,r2,rz,s1,s2,s3,s4,s5,s6;int seq;LocalDate day=LocalDate.now(ZoneOffset.ofHours(7)).plusDays(20);
 @BeforeAll void setup()throws Exception{
  assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).startsWith("cinema_management_test_");
  for(String role:List.of("ADMIN","STAFF")){
   String email=role.toLowerCase()+key+"@example.invalid";jdbc.update("INSERT INTO users(username,email,display_name,password_hash,status) VALUES(?,?,?,?, 'ACTIVE')",role+key,email,"Stats test",encoder.encode(password));
   jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u JOIN roles r ON r.code=? WHERE u.email=?",role,email);
  }
  jdbc.update("INSERT INTO users(username,email,display_name,status) VALUES(?,?,?,'INACTIVE')",prefix,prefix+"@example.invalid","Stats fixture");actor=jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,prefix);
  a=movie("A");b=movie("B");r1=room(3);r2=room(2);rz=room(0);
  s1=show(a,r1,day.atTime(23,0),4,false);s2=show(b,r2,day.atStartOfDay(),null,false);s3=show(a,r1,day.plusDays(1).atTime(2,0),null,true);s4=show(b,r2,day.plusDays(1).atStartOfDay(),2,false);s5=show(a,r1,LocalDate.of(2001,1,1).atStartOfDay(),null,false);s6=show(b,rz,day.atTime(12,0),null,false);
  List<Long> x=jdbc.queryForList("SELECT id FROM seats WHERE room_id=? ORDER BY seat_number",Long.class,r1),y=jdbc.queryForList("SELECT id FROM seats WHERE room_id=? ORDER BY seat_number",Long.class,r2);
  booking(s1,r1,List.of(x.get(0),x.get(1)),day.atStartOfDay());booking(s1,r1,List.of(x.get(0)),null);booking(s1,r1,List.of(x.get(1),x.get(2)),null);
  booking(s4,r2,List.of(y.get(0)),day.minusDays(1).atTime(23,59,59));booking(s4,r2,List.of(y.get(0)),null);
  admin=login("admin");staff=login("staff");evidence.put("fixture",Map.of("day",day.toString(),"movieA",a,"movieB",b,"room1",r1,"room2",r2,"snapshotShow",s1,"historicalShow",s5,"zeroCapacityShow",s6));
 }
 long movie(String tag){String code=prefix+tag;jdbc.update("INSERT INTO movies(code,title,duration_minutes,minimum_age,status) VALUES(?,?,120,0,'ACTIVE')",code,code);long id=jdbc.queryForObject("SELECT id FROM movies WHERE code=?",Long.class,code);jdbc.update("INSERT INTO movie_genres(movie_id,genre_id) VALUES(?,1),(?,3)",id,id);return id;}
 long room(int count){String code=prefix+"R"+(++seq);jdbc.update("INSERT INTO rooms(code,name) VALUES(?,?)",code,code);long id=jdbc.queryForObject("SELECT id FROM rooms WHERE code=?",Long.class,code);for(int i=1;i<=count;i++)jdbc.update("INSERT INTO seats(room_id,code,row_label,seat_number,seat_type,status) VALUES(?,?,'A',?,?,'ACTIVE')",id,"A"+i,i,i==2?"VIP":"STANDARD");return id;}
 long show(long movie,long room,LocalDateTime start,Integer capacity,boolean cancelled){jdbc.update("INSERT INTO showtimes(movie_id,room_id,duration_minutes_snapshot,minimum_age_snapshot,starts_at,ends_at,standard_price,vip_price,capacity_snapshot,status,cancelled_at,cancelled_by,cancel_reason) VALUES(?,?,120,0,?,?,80000,100000,?,?,?,?,?)",movie,room,start,start.plusHours(2),capacity,cancelled?"CANCELLED":"SCHEDULED",cancelled?start.minusDays(1):null,cancelled?actor:null,cancelled?"Stats fixture":null);return jdbc.queryForObject("SELECT MAX(id) FROM showtimes WHERE room_id=?",Long.class,room);}
 void booking(long show,long room,List<Long> seats,LocalDateTime cancel){String code=prefix+"B"+(++seq);long total=seats.stream().mapToLong(id->jdbc.queryForObject("SELECT seat_type FROM seats WHERE id=?",String.class,id).equals("VIP")?100000:80000).sum();jdbc.update("INSERT INTO bookings(code,showtime_id,room_id,created_by,created_by_name_snapshot,movie_title_snapshot,room_code_snapshot,price_version_snapshot,total_amount,age_checked,created_at) VALUES(?,?,?,?, 'Fixture','Fixture','Fixture',0,?,1,?)",code,show,room,actor,total,day.minusDays(2).atStartOfDay());long id=jdbc.queryForObject("SELECT id FROM bookings WHERE code=?",Long.class,code);for(long seat:seats){String type=jdbc.queryForObject("SELECT seat_type FROM seats WHERE id=?",String.class,seat);String label=jdbc.queryForObject("SELECT code FROM seats WHERE id=?",String.class,seat);jdbc.update("INSERT INTO booking_seats(booking_id,showtime_id,room_id,seat_id,seat_code_snapshot,seat_type_snapshot,unit_price) VALUES(?,?,?,?,?,?,?)",id,show,room,seat,label,type,type.equals("VIP")?100000:80000);}if(cancel!=null)jdbc.update("UPDATE bookings SET status='CANCELLED',cancelled_at=?,cancelled_by=?,cancel_reason='Stats fixture' WHERE id=?",cancel,actor,id);}
 class Browser{final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String csrf;HttpResponse<String> call(String method,String path,Object value)throws Exception{var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Accept","application/json");if(value!=null)r.header("Content-Type","application/json");if(csrf!=null)r.header("X-CSRF-TOKEN",csrf);return http.send(r.method(method,value==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value))).build(),HttpResponse.BodyHandlers.ofString());}}
 Browser login(String role)throws Exception{Browser b=new Browser();b.csrf=body(b.call("GET","/api/auth/csrf",null),200).get("token").asText();body(b.call("POST","/api/auth/login",Map.of("email",role+key+"@example.invalid","password",password)),200);return b;}
 JsonNode body(HttpResponse<String> r,int status)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return json.readTree(r.body());}
 String path(String metric){return "/api/admin/statistics/"+metric+"?from="+day+"&to="+day.plusDays(1)+"&size=100";}
 JsonNode stats(String metric,String extra)throws Exception{return body(admin.call("GET",path(metric)+extra,null),200);}
 @Test void showtimesAndSeatsMatchHandCalculation()throws Exception{
  var daily=stats("showtimes","&movieId="+a);assertThat(daily.get("items").get(0).get("totalShowtimes").asInt()).isEqualTo(1);assertThat(daily.get("items").get(1).get("cancelledShowtimes").asInt()).isEqualTo(1);
  var all=stats("showtimes","");JsonNode first=all.get("items").get(0); // isolate our rows via response room/movie filters in expected evidence below
  var movies=stats("booked-seats","&movieId="+a);assertThat(movies.get("items").size()).isEqualTo(1);var film=movies.get("items").get(0);assertThat(film.get("activeBookings").asInt()).isEqualTo(2);assertThat(film.get("bookedSeats").asInt()).isEqualTo(3);assertThat(film.get("bookingValue").decimalValue()).isEqualByComparingTo("260000");
  var filmB=stats("booked-seats","&movieId="+b).get("items").get(0);assertThat(filmB.get("bookedSeats").asInt()).isEqualTo(1);assertThat(filmB.get("bookingValue").decimalValue()).isEqualByComparingTo("80000");evidence.put("showtimesMovieA",daily);evidence.put("bookedSeatsMovieA",movies);
 }
 @Test void occupancySnapshotZeroUnknownAndNoClamping()throws Exception{
  var rows=stats("occupancy","&roomId="+r1);var item=rows.get("items").get(0);assertThat(item.get("occupancyPercent").decimalValue()).isEqualByComparingTo("75.00");evidence.put("occupancyRoom1",rows);
  jdbc.update("UPDATE showtimes SET capacity_snapshot=2 WHERE id=?",s1);try{var bad=stats("occupancy","&roomId="+r1).get("items").get(0);assertThat(bad.get("occupancyPercent").decimalValue()).isEqualByComparingTo("150.00");assertThat(bad.get("anomaly").asBoolean()).isTrue();}finally{jdbc.update("UPDATE showtimes SET capacity_snapshot=4 WHERE id=?",s1);}
  var zero=stats("occupancy","&roomId="+rz).get("items").get(0);assertThat(zero.get("capacity").asInt()).isZero();assertThat(zero.get("occupancyPercent").isNull()).isTrue();
  var history=body(admin.call("GET","/api/admin/statistics/occupancy?from=2001-01-01&to=2001-01-01&movieId="+a,null),200).get("items").get(0);assertThat(history.get("capacity").isNull()).isTrue();assertThat(history.get("capacityBasis").asText()).isEqualTo("MISSING_HISTORICAL_CAPACITY");evidence.put("missingHistoricalCapacity",history);evidence.put("zeroCapacity",zero);
 }
 @Test void cancellationUsesEventDayNotCreationOrShowDay()throws Exception{
  var report=stats("cancellations","&movieId="+a);var row=report.get("items").get(0);assertThat(row.get("cancelledBookings").asInt()).isEqualTo(1);assertThat(row.get("cancelledSeats").asInt()).isEqualTo(2);assertThat(row.get("cancelledBookingValue").decimalValue()).isEqualByComparingTo("180000");assertThat(report.get("items").get(1).get("cancelledBookings").asInt()).isZero();
  assertThat(stats("cancellations","&movieId="+b).get("items").get(0).get("cancelledBookings").asInt()).isZero();evidence.put("cancellationsMovieA",report);
 }
 @Test void emptyInvalidAndPermissions()throws Exception{
  assertThat(stats("occupancy","&movieId="+Long.MAX_VALUE).get("items").isEmpty()).isTrue();assertThat(stats("showtimes","&movieId="+Long.MAX_VALUE).get("items").get(0).get("totalShowtimes").asInt()).isZero();
  assertThat(stats("occupancy","&roomId="+r1+"&page=99").get("items").isEmpty()).isTrue();
  for(String q:List.of("from=2026-02-30&to=2026-03-01","from=2026-03-01&to=2026-02-01","from=2026-01-01&to=2026-02-01","from=2026-01-01&to=2026-01-02&size=101"))body(admin.call("GET","/api/admin/statistics/occupancy?"+q,null),400);
  for(String metric:List.of("showtimes","booked-seats","occupancy","cancellations")){body(staff.call("GET",path(metric),null),403);body(new Browser().call("GET",path(metric),null),401);}
 }
 @AfterAll void finish()throws Exception{for(String role:List.of("admin","staff")){String email=role+key+"@example.invalid";jdbc.update("DELETE ur FROM user_roles ur JOIN users u ON u.id=ur.user_id WHERE u.email=?",email);jdbc.update("DELETE FROM users WHERE email=?",email);}Files.writeString(Path.of("target/statistics-evidence.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));}
}
