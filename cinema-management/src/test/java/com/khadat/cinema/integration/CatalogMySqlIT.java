package com.khadat.cinema.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.net.*;
import java.net.http.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP, sessions, CSRF and existing MySQL schema. Fixtures have a unique owner prefix. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.jpa.properties.hibernate.generate_statistics=true")
@EnabledIfEnvironmentVariable(named="CINEMA_MYSQL_IT",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogMySqlIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired EntityManagerFactory emf;
    @LocalServerPort int port;
    final String suffix=UUID.randomUUID().toString().replace("-","").substring(0,16);
    final String prefix="CAT-"+suffix;
    final String adminEmail="catalog-admin-"+suffix+"@example.invalid";
    final String staffEmail="catalog-staff-"+suffix+"@example.invalid";
    final String password=secret();
    final List<Long> movieIds=new ArrayList<>();
    final List<Long> genreIds=new ArrayList<>();
    Browser admin,staff;
    long userId,roomId;
    int sequence;

    private static String secret() {
        byte[] bytes=new byte[24];new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    @BeforeAll void prepare() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).startsWith("cinema_management_test_");
        for (String email:List.of(adminEmail,staffEmail)) {
            jdbc.update("INSERT INTO users(username,email,display_name,password_hash,status) VALUES(?,?,?,?, 'ACTIVE')",
                email.substring(0,email.indexOf('@')),email,"Catalog test fixture",encoder.encode(password));
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u JOIN roles r ON r.code=? WHERE u.email=?",
                email.equals(adminEmail)?"ADMIN":"STAFF",email);
        }
        userId=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,adminEmail);
        roomId=jdbc.queryForObject("SELECT MIN(id) FROM rooms",Long.class);
        admin=login(adminEmail);staff=login(staffEmail);
    }
    @AfterEach void cleanCatalogFixtures() {
        // These IDs originate only from this test class; no seed or real rows are deleted.
        for (long id:movieIds) {
            jdbc.update("DELETE FROM showtimes WHERE movie_id=?",id);
            jdbc.update("DELETE FROM movie_genres WHERE movie_id=?",id);
            jdbc.update("DELETE FROM movies WHERE id=?",id);
        }
        movieIds.clear();
        for (long id:genreIds) jdbc.update("DELETE FROM genres WHERE id=?",id);
        genreIds.clear();
    }
    @AfterAll void cleanAccounts() {
        for (String email:List.of(adminEmail,staffEmail)) {
            jdbc.update("DELETE ur FROM user_roles ur JOIN users u ON u.id=ur.user_id WHERE u.email=?",email);
            jdbc.update("DELETE FROM users WHERE email=?",email);
        }
    }
    private final class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        String csrf;
        HttpResponse<String> call(String method,String path,Object body,boolean token) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Accept","application/json");
            if (token && csrf!=null) request.header("X-CSRF-TOKEN",csrf);
            if (body!=null) request.header("Content-Type","application/json");
            return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():
                HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        void token() throws Exception { csrf=body(call("GET","/api/auth/csrf",null,false),200).get("token").asText(); }
    }
    private Browser login(String email) throws Exception {
        Browser b=new Browser();b.token();
        body(b.call("POST","/api/auth/login",Map.of("email",email,"password",password),true),200);
        b.token();return b;
    }
    private JsonNode body(HttpResponse<String> response,int expected) throws Exception {
        assertThat(response.statusCode()).as("HTTP %s: %s",expected,response.body()).isEqualTo(expected);
        assertThat(response.headers().firstValue("location")).satisfies(location -> {
            if (expected!=201) assertThat(location).isEmpty();
        });
        return json.readTree(response.body());
    }
    private JsonNode error(HttpResponse<String> response,int status,String code) throws Exception {
        JsonNode result=body(response,status);
        assertThat(result.get("code").asText()).isEqualTo(code);
        assertThat(result.get("fieldErrors").isObject()).isTrue();
        assertThat(result.has("trace")||result.has("exception")||response.body().contains(password)).isFalse();
        return result;
    }
    private long genre() throws Exception { return genre(prefix+" Genre "+(++sequence)); }
    private long genre(String name) throws Exception {
        var response=admin.call("POST","/api/admin/genres",Map.of("name",name,"description","Fixture"),true);
        JsonNode created=body(response,201);long id=created.get("id").asLong();genreIds.add(id);
        assertThat(response.headers().firstValue("location")).hasValue("/api/admin/genres/"+id);return id;
    }
    private Map<String,Object> request(List<Long> genres) {
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("code",prefix+"-"+(++sequence));payload.put("title",prefix+" Movie "+sequence);
        payload.put("description","Catalog fixture");payload.put("durationMinutes",120);payload.put("minimumAge",13);
        payload.put("posterUrl","https://example.invalid/poster.jpg");payload.put("trailerUrl",null);
        payload.put("status","ACTIVE");payload.put("genreIds",genres);return payload;
    }
    private JsonNode movie(Map<String,Object> payload) throws Exception {
        var response=admin.call("POST","/api/admin/movies",payload,true);JsonNode created=body(response,201);
        movieIds.add(created.get("id").asLong());
        assertThat(response.headers().firstValue("location")).hasValue("/api/admin/movies/"+created.get("id").asLong());
        return created;
    }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class); }
    private void showtime(long movieId,boolean future,boolean cancelled) {
        LocalDateTime start=LocalDateTime.now().plusDays(future?3+movieId:-3-movieId).withSecond(0).withNano(0);
        jdbc.update("INSERT INTO showtimes(movie_id,room_id,duration_minutes_snapshot,minimum_age_snapshot,starts_at,ends_at,standard_price,vip_price,status,cancelled_at,cancelled_by,cancel_reason) VALUES(?,?,120,13,?,?,80000,100000,?,?,?,?)",
            movieId,roomId,start,start.plusMinutes(120),cancelled?"CANCELLED":"SCHEDULED",cancelled?start.minusDays(1):null,
            cancelled?userId:null,cancelled?"Fixture cancellation":null);
    }
    @Test void genreCrudAndFullPut() throws Exception {
        long id=genre("  "+prefix+" Trimmed  ");
        assertThat(body(admin.call("GET","/api/admin/genres/"+id,null,false),200).get("name").asText()).isEqualTo(prefix+" Trimmed");
        JsonNode updated=body(admin.call("PUT","/api/admin/genres/"+id,Map.of("name",prefix+" Updated"),true),200);
        assertThat(updated.get("description").isNull()).isTrue();
        assertThat(admin.call("DELETE","/api/admin/genres/"+id,null,true).statusCode()).isEqualTo(204);
        error(admin.call("GET","/api/admin/genres/"+id,null,false),404,"RESOURCE_NOT_FOUND");
    }
    @Test void genreValidationAndDatabaseCollationDuplicates() throws Exception {
        long id=genre(prefix+" Cà phê");
        JsonNode duplicate=error(admin.call("POST","/api/admin/genres",Map.of("name",prefix.toLowerCase()+" Ca phe"),true),409,"GENRE_NAME_EXISTS");
        assertThat(duplicate.get("fieldErrors").has("name")).isTrue();
        for (String name:List.of("   ","x".repeat(101)))
            assertThat(error(admin.call("PUT","/api/admin/genres/"+id,Map.of("name",name),true),400,"VALIDATION_FAILED").get("fieldErrors").has("name")).isTrue();
    }
    @Test void movieCrudMultipleGenresDeduplicationAndReplacement() throws Exception {
        long a=genre(),b=genre(),c=genre();Map<String,Object> request=request(List.of(b,a,b));
        JsonNode created=movie(request);long id=created.get("id").asLong();
        assertThat(created.get("genreIds").toString()).isEqualTo("["+a+","+b+"]");
        assertThat(created.get("genres").size()).isEqualTo(2);
        request.put("genreIds",List.of(c));request.put("title",prefix+" Changed");
        request.remove("description");request.remove("posterUrl");
        JsonNode updated=body(admin.call("PUT","/api/admin/movies/"+id,request,true),200);
        assertThat(updated.get("description").isNull()&&updated.get("posterUrl").isNull()).isTrue();
        JsonNode detail=body(admin.call("GET","/api/admin/movies/"+id,null,false),200);
        assertThat(detail.get("genreIds").toString()).isEqualTo("["+c+"]");
        assertThat(detail.get("title").asText()).isEqualTo(prefix+" Changed");
        assertThat(admin.call("DELETE","/api/admin/movies/"+id,null,true).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movie_genres WHERE movie_id=?",Long.class,id)).isZero();
        body(admin.call("GET","/api/admin/genres/"+a,null,false),200);
    }
    @Test void missingGenreRollsBackCreateAndUpdate() throws Exception {
        long a=genre(),b=genre();Map<String,Object> payload=request(List.of(a));
        long id=movie(payload).get("id").asLong();long before=count("movies"),links=count("movie_genres");
        Map<String,Object> invalid=request(List.of(b,Long.MAX_VALUE));
        JsonNode fail=error(admin.call("POST","/api/admin/movies",invalid,true),400,"UNKNOWN_GENRES");
        assertThat(fail.get("fieldErrors").has("genreIds")).isTrue();
        invalid.put("code",payload.get("code"));invalid.put("title","Must not persist");
        error(admin.call("PUT","/api/admin/movies/"+id,invalid,true),400,"UNKNOWN_GENRES");
        JsonNode detail=body(admin.call("GET","/api/admin/movies/"+id,null,false),200);
        assertThat(detail.get("title").asText()).isEqualTo(payload.get("title"));
        assertThat(detail.get("genreIds").toString()).isEqualTo("["+a+"]");
        assertThat(count("movies")).isEqualTo(before);assertThat(count("movie_genres")).isEqualTo(links);
    }
    @Test void movieFieldValidationIncludingFractionsAndUnsafeUrls() throws Exception {
        long genre=genre();
        for (Map.Entry<String,Object> invalid:List.<Map.Entry<String,Object>>of(
            Map.entry("title"," "),Map.entry("title","x".repeat(201)),Map.entry("code","非ASCII"),
            Map.entry("durationMinutes",0),Map.entry("durationMinutes",-1),Map.entry("durationMinutes",65536),
            Map.entry("minimumAge",-1),Map.entry("minimumAge",19),Map.entry("status","DELETED"),
            Map.entry("posterUrl","javascript:alert(1)"),Map.entry("trailerUrl","data:text/html,x"),
            Map.entry("posterUrl","ftp://example.invalid/x"),Map.entry("posterUrl","/relative.jpg"),
            Map.entry("posterUrl","https://user:secret@example.invalid/x"),Map.entry("description","ấ".repeat(22000)),
            Map.entry("posterUrl","https://example.invalid:99999/x"),
            Map.entry("genreIds",List.of()),Map.entry("genreIds",List.of(0)))) {
            Map<String,Object> payload=request(List.of(genre));payload.put(invalid.getKey(),invalid.getValue());
            JsonNode failure=error(admin.call("POST","/api/admin/movies",payload,true),400,"VALIDATION_FAILED");
            assertThat(failure.get("fieldErrors").size()).isPositive();
        }
        for (String field:List.of("title","code","durationMinutes","minimumAge","status","genreIds")) {
            Map<String,Object> payload=request(List.of(genre));payload.remove(field);
            error(admin.call("POST","/api/admin/movies",payload,true),400,"VALIDATION_FAILED");
        }
        for (String field:List.of("durationMinutes","minimumAge","genreIds")) {
            Map<String,Object> payload=request(List.of(genre));payload.put(field,field.equals("genreIds")?List.of(1.5):1.5);
            error(admin.call("POST","/api/admin/movies",payload,true),400,"INVALID_REQUEST");
        }
    }
    @Test void movieCodeUniqueButTitleCanRepeat() throws Exception {
        long genre=genre();Map<String,Object> first=request(List.of(genre));movie(first);
        Map<String,Object> second=request(List.of(genre));second.put("code",first.get("code").toString().toLowerCase());
        assertThat(error(admin.call("POST","/api/admin/movies",second,true),409,"MOVIE_CODE_EXISTS").get("fieldErrors").has("code")).isTrue();
        second.put("code",prefix+"-UNIQUE");second.put("title",first.get("title"));movie(second);
    }
    @Test void genreInUseCannotBeDeleted() throws Exception {
        long genre=genre();long movie=movie(request(List.of(genre))).get("id").asLong();
        error(admin.call("DELETE","/api/admin/genres/"+genre,null,true),409,"GENRE_IN_USE");
        body(admin.call("GET","/api/admin/movies/"+movie,null,false),200);
    }
    @Test void anyShowtimeHistoryPreventsDeleteAndDurationOrAgeChange() throws Exception {
        for (boolean cancelled:List.of(false,true)) {
            long genre=genre();Map<String,Object> payload=request(List.of(genre));long id=movie(payload).get("id").asLong();
            showtime(id,false,cancelled);
            LocalDateTime end=jdbc.queryForObject("SELECT ends_at FROM showtimes WHERE movie_id=?",LocalDateTime.class,id);
            error(admin.call("DELETE","/api/admin/movies/"+id,null,true),409,"MOVIE_HAS_SHOWTIMES");
            payload.put("durationMinutes",150);
            error(admin.call("PUT","/api/admin/movies/"+id,payload,true),409,"MOVIE_SCHEDULE_FIELDS_LOCKED");
            payload.put("durationMinutes",120);payload.put("minimumAge",16);
            error(admin.call("PUT","/api/admin/movies/"+id,payload,true),409,"MOVIE_SCHEDULE_FIELDS_LOCKED");
            payload.put("minimumAge",13);payload.put("description","Allowed metadata edit");payload.put("status","INACTIVE");
            body(admin.call("PUT","/api/admin/movies/"+id,payload,true),200);
            assertThat(jdbc.queryForObject("SELECT ends_at FROM showtimes WHERE movie_id=?",LocalDateTime.class,id)).isEqualTo(end);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM showtimes WHERE movie_id=?",Long.class,id)).isEqualTo(1);
        }
    }
    @Test void futureShowtimePreventsDeactivation() throws Exception {
        long genre=genre();Map<String,Object> payload=request(List.of(genre));long id=movie(payload).get("id").asLong();
        showtime(id,true,false);payload.put("status","INACTIVE");
        error(admin.call("PUT","/api/admin/movies/"+id,payload,true),409,"MOVIE_HAS_UNFINISHED_SHOWTIMES");
        assertThat(body(admin.call("GET","/api/admin/movies/"+id,null,false),200).get("status").asText()).isEqualTo("ACTIVE");
    }
    @Test void missingResourcesAre404AndBadIdsAre400() throws Exception {
        long genre=genre();
        for (String type:List.of("movies","genres")) {
            String path="/api/admin/"+type+"/"+Long.MAX_VALUE;
            error(admin.call("GET",path,null,false),404,"RESOURCE_NOT_FOUND");
            error(admin.call("DELETE",path,null,true),404,"RESOURCE_NOT_FOUND");
            error(admin.call("PUT",path,type.equals("movies")?request(List.of(genre)):Map.of("name","Valid"),true),404,"RESOURCE_NOT_FOUND");
            error(admin.call("GET","/api/admin/"+type+"/0",null,false),400,"VALIDATION_FAILED");
        }
    }
    @Test void movieSearchFilterStablePagesAndBoundedRelationQueries() throws Exception {
        long a=genre(),b=genre();
        for (int i=0;i<3;i++) { Map<String,Object> p=request(List.of(a,b));p.put("title",prefix+" Same");movie(p); }
        Map<String,Object> inactive=request(List.of(b));inactive.put("status","INACTIVE");movie(inactive);
        String path="/api/admin/movies?q="+prefix+"&genreId="+a+"&status=ACTIVE&sort=title&size=2";
        var stats=emf.unwrap(SessionFactory.class).getStatistics();long before=stats.getPrepareStatementCount();
        JsonNode first=body(admin.call("GET",path,null,false),200);
        assertThat(stats.getPrepareStatementCount()-before).as("Page query, count, one batched genre query").isLessThanOrEqualTo(3);
        JsonNode second=body(admin.call("GET",path+"&page=1",null,false),200);
        assertThat(first.get("total").asInt()).isEqualTo(3);assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        assertThat(first.get("items").size()).isEqualTo(2);assertThat(second.get("items").size()).isEqualTo(1);
        assertThat(first.get("items").get(0).get("genres").size()).isEqualTo(2);
        assertThat(first.get("items").get(1).get("id").asLong()).isLessThan(second.get("items").get(0).get("id").asLong());
        assertThat(body(admin.call("GET","/api/admin/movies?genreId="+Long.MAX_VALUE,null,false),200).get("total").asLong()).isZero();
    }
    @Test void genreSearchAndLiteralWildcardsAndInvalidPaging() throws Exception {
        genre(prefix+" 100%_literal!");genre(prefix+" 100xxliteral!");
        String q=URLEncoder.encode(prefix+" 100%_",java.nio.charset.StandardCharsets.UTF_8);
        JsonNode page=body(admin.call("GET","/api/admin/genres?q="+q+"&size=1&sort=name&direction=desc",null,false),200);
        assertThat(page.get("total").asInt()).isEqualTo(1);
        assertThat(body(admin.call("GET","/api/admin/genres?q=missing-"+suffix+"&page=999",null,false),200).get("items").isEmpty()).isTrue();
        for (String type:List.of("genres","movies")) for (String query:List.of("page=-1","size=0","size=101","sort=passwordHash","direction=sideways"))
            error(admin.call("GET","/api/admin/"+type+"?"+query,null,false),400,"VALIDATION_FAILED");
        error(admin.call("GET","/api/admin/movies?status=DELETED",null,false),400,"VALIDATION_FAILED");
        error(admin.call("GET","/api/admin/movies?page=no",null,false),400,"INVALID_REQUEST");
    }
    @Test void realAdminAllowedStaffDeniedAndCsrfRequired() throws Exception {
        long g=genre();Map<String,Object> p=request(List.of(g));long id=movie(p).get("id").asLong();
        Browser anonymous=new Browser();
        for (String type:List.of("genres","movies")) {
            String path="/api/admin/"+type;
            error(anonymous.call("GET",path,null,false),401,"UNAUTHENTICATED");
            error(staff.call("GET",path,null,false),403,"FORBIDDEN");
            for (String method:List.of("POST","PUT","DELETE")) {
                String itemPath=path+(method.equals("POST")?"":"/"+(type.equals("genres")?g:id));
                error(staff.call(method,itemPath,method.equals("DELETE")?null:Map.of(),true),403,"FORBIDDEN");
            }
            error(admin.call("POST",path,Map.of(),false),403,"CSRF_INVALID");
        }
        assertThat(body(staff.call("GET","/api/auth/me",null,false),200).get("roles").get(0).asText()).isEqualTo("STAFF");
    }
}
