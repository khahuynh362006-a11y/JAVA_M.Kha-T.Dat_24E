package com.khadat.cinema.integration;

import com.khadat.cinema.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "CINEMA_MYSQL_IT", matches = "true")
@Transactional
class FoundationMySqlIT {
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    @BeforeEach void requireDisposableDatabase() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).startsWith("cinema_management_test_");
    }

    @Test void validatesAllElevenEntitiesAgainstExistingSchema() {
        assertThat(emf.getMetamodel().getEntities()).hasSize(11);
        assertThat(em.find(Movie.class,1L).getDurationMinutes()).isEqualTo(120);
        assertThat(em.find(Movie.class,1L).getMinimumAge()).isEqualTo(13);
        assertThat(em.find(Seat.class,1L).getRoom().getCode()).isEqualTo("P01");
        assertThat(em.find(Showtime.class,1L).getStandardPrice()).isEqualByComparingTo("80000");
        assertThat(em.find(Showtime.class,1L).getStartsAt().getHour()).isEqualTo(18);
    }

    @Test void compositeMovieGenreKeyLoadsLazyRelations() {
        MovieGenre link = em.find(MovieGenre.class,new MovieGenreId(1L,1L));
        assertThat(link.getMovie().getCode()).isEqualTo("DEMO-M01");
        assertThat(link.getGenre().getName()).isNotBlank();
        assertThat(new MovieGenreId(1L,1L)).isEqualTo(new MovieGenreId(1L,1L));
    }

    @Test void generatedColumnsAndTriggerStateSurviveJpaRoundTrip() {
        User user = new User();
        String unique = UUID.randomUUID().toString();
        user.setUsername(unique); user.setEmail(unique+"@example.invalid");
        user.setDisplayName("Foundation fixture");user.setStatus("INACTIVE");
        em.persist(user);em.flush();
        assertThat(user.getCreatedAt()).isNotNull();
        UserRole role = new UserRole();role.setId(new UserRoleId(user.getId(),2L));
        em.persist(role);em.flush();em.refresh(role);
        assertThat(role.getRole().getCode()).isEqualTo("STAFF");
        Booking booking = booking(user,"IT-"+unique);
        em.persist(booking);em.flush();
        BookingSeat detail = new BookingSeat();
        detail.setBookingId(booking.getId());detail.setShowtimeId(1L);detail.setRoomId(1L);detail.setSeatId(1L);
        detail.setSeatCodeSnapshot("A1");detail.setSeatTypeSnapshot("STANDARD");detail.setUnitPrice(new BigDecimal("80000"));
        em.persist(detail);em.flush();em.refresh(detail);
        assertThat(detail.getBookingStatus()).isEqualTo("CONFIRMED");
        assertThat(detail.getActiveSeatId()).isEqualTo(1L);
        assertThat(detail.getBooking().getCode()).isEqualTo(booking.getCode());
        assertThat(detail.getSeat().getRoomId()).isEqualTo(1L);
        booking.setStatus("CANCELLED");booking.setCancelledAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        booking.setCancelledBy(user.getId());booking.setCancelReason("Integration test rollback fixture");
        em.flush();em.refresh(detail);
        assertThat(detail.getBookingStatus()).isEqualTo("CANCELLED");
        assertThat(detail.getActiveSeatId()).isNull();
        assertThat(detail.getUnitPrice()).isEqualByComparingTo("80000");
        // @Transactional rolls back user, role, booking and trigger effects.
    }

    @Test void realHttpHealthQueriesDatabase() throws Exception {
        HttpResponse<String> response = request("/api/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"","\"database\":\"UP\"");
        assertThat(response.body()).doesNotContain("jdbc", "root", "password");
    }

    @Test void unknownRouteIsDeniedBeforeMvcForAnonymousClient() throws Exception {
        HttpResponse<String> response = request("/api/foundation-does-not-exist");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHENTICATED","fieldErrors","timestamp");
        assertThat(response.body()).doesNotContain("stackTrace","Exception");
    }

    private HttpResponse<String> request(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .GET().build(),HttpResponse.BodyHandlers.ofString());
    }

    private Booking booking(User user,String code) {
        Booking b = new Booking();b.setCode(code);b.setShowtimeId(1L);b.setRoomId(1L);b.setCreatedBy(user.getId());
        b.setCreatedByNameSnapshot(user.getDisplayName());b.setMovieTitleSnapshot("Demo");b.setRoomCodeSnapshot("P01");
        b.setPriceVersionSnapshot(0L);b.setTotalAmount(new BigDecimal("80000"));b.setAgeChecked(true);b.setStatus("CONFIRMED");
        return b;
    }
}
