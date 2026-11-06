package com.cineverse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cineverse.admin.*;
import com.cineverse.auth.*;
import com.cineverse.catalog.*;
import com.cineverse.community.*;
import com.cineverse.email.*;
import com.cineverse.payments.*;
import com.cineverse.shared.*;
import com.cineverse.streaming.*;
import com.cineverse.users.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.*;
import java.time.*;
import java.util.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CineVerseIntegrationTest {
    @Autowired com.cineverse.booking.BookingService bookings;
    @Autowired com.cineverse.booking.CinemaAdminService cinemaAdmin;
    static Path media;

    static {
        try {
            media = Files.createTempDirectory("cineverse-media-test-");
            Files.write(media.resolve("sintel.mp4"), new byte[1024]);
            Files.write(media.resolve("bunny.mp4"), new byte[512]);
            Files.write(media.resolve("bunny-full.mp4"), new byte[512]);
            Files.writeString(media.resolve("demo-vi.vtt"), "WEBVTT\n\n");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("app.media-root", media::toString);
        r.add("app.upload-root", () -> media.resolve("uploads").toString());
    }

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired AuthTokenRepository tokens;
    @Autowired CatalogService catalog;
    @Autowired MovieRepository movies;
    @Autowired CommunityService community;
    @Autowired PaymentService payments;
    @Autowired VnpaySandboxPaymentGateway vnpay;
    @Autowired PayosPaymentGateway payos;
    @Autowired PaymentOrderRepository orders;
    @Autowired JdbcTemplate db;
    @Autowired MediaService streaming;
    @Autowired AdminService admin;
    @Autowired ObjectMapper json;
    @Autowired OutboxWorker outbox;
    @Autowired UploadService uploads;

    Map<String, String> vnpayResult(PaymentOrder order, String code, String transaction) {
        Map<String, String> values =
                new HashMap<>(
                        Map.of(
                                "vnp_TmnCode",
                                "TEST_TMN",
                                "vnp_TxnRef",
                                order.id,
                                "vnp_Amount",
                                Long.toString(order.amount * 100),
                                "vnp_OrderInfo",
                                VnpaySandboxPaymentGateway.info(order),
                                "vnp_ResponseCode",
                                code,
                                "vnp_TransactionStatus",
                                code.equals("00") ? "00" : "02",
                                "vnp_TransactionNo",
                                transaction));
        signVnpay(values);
        return values;
    }

    void signVnpay(Map<String, String> values) {
        values.put(
                "vnp_SecureHash",
                VnpaySignature.sign(
                        "UNIT_TEST_VNPAY_SECRET_AT_LEAST_32", VnpaySignature.canonical(values)));
    }

    PaymentOrder vnpayBooking() {
        var booking = holdSeats(1);
        var created = payments.createForBooking(free().id, booking.id());
        var order = orders.findById(created.id()).orElseThrow();
        order.provider = "VNPAY_SANDBOX";
        order.payUrl = vnpay.create(order);
        orders.flush();
        return order;
    }

    @Test
    void vnpayIpnIssuesTicketsOnceAndRejectsTamperedAmount() {
        var order = vnpayBooking();
        var values = vnpayResult(order, "00", "987654321");
        values.put("vnp_Amount", "1");
        assertEquals("97", payments.vnpayIpn(values).get("RspCode"));
        signVnpay(values);
        assertEquals("04", payments.vnpayIpn(values).get("RspCode"));
        assertEquals("PENDING", order.status);
        values = vnpayResult(order, "00", "987654321");
        assertEquals("00", payments.vnpayIpn(values).get("RspCode"));
        assertEquals("02", payments.vnpayIpn(values).get("RspCode"));
        assertEquals("CONFIRMED", bookings.get(free().id, order.bookingId, false).status());
        assertEquals(order.amount, ((Number) admin.dashboard(7).get("sandboxRevenue")).longValue());
        assertEquals(1, bookings.get(free().id, order.bookingId, false).tickets().size());
    }

    @Test
    void vnpayCancelReleasesSeatsAndLateSuccessNeedsReview() {
        var order = vnpayBooking();
        payments.vnpayIpn(vnpayResult(order, "24", "0"));
        assertEquals("CANCELLED", bookings.get(free().id, order.bookingId, false).status());
        payments.vnpayIpn(vnpayResult(order, "00", "987654322"));
        assertEquals("PAYMENT_REVIEW", bookings.get(free().id, order.bookingId, false).status());
        assertTrue(bookings.get(free().id, order.bookingId, false).tickets().isEmpty());
        assertEquals(
                0,
                db.queryForObject(
                        "select count(*) from email_outbox where dedup_key=?",
                        Long.class,
                        "receipt:" + order.id));
    }

    @Test
    void vnpayReturnOnlyRedirectsAndDoesNotMarkPaid() throws Exception {
        var order = vnpayBooking();
        var request = get("/api/v1/payments/vnpay/return");
        vnpayResult(order, "00", "987654323").forEach(request::param);
        mvc.perform(request)
                .andExpect(status().isFound())
                .andExpect(
                        header().string(
                                        "Location",
                                        org.hamcrest.Matchers.containsString(
                                                "orderId=" + order.id)));
        assertEquals("PENDING", order.status);
        assertTrue(bookings.get(free().id, order.bookingId, false).tickets().isEmpty());
    }

    @Test
    void vnpayCallbackRejectsRepeatedParametersAndReconciliationChecksOwner() throws Exception {
        mvc.perform(get("/api/v1/payments/vnpay/ipn").param("vnp_Amount", "1", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("RspCode").value("97"));
        var order = vnpayBooking();
        mvc.perform(post("/api/v1/payments/" + order.id + "/reconcile").with(as(vip())))
                .andExpect(status().isForbidden());
    }

    @MockitoBean JavaMailSender mail;

    PaymentOrder payosBooking() {
        var order = vnpayBooking();
        order.provider = "PAYOS";
        db.update(
                "insert into payos_links(order_code,order_id,payment_link_id,created_at)"
                        + " values(?,?,?,?)",
                987123L,
                order.id,
                "payos-test-link",
                java.sql.Timestamp.from(Instant.now()));
        return order;
    }

    vn.payos.model.v2.paymentRequests.PaymentLink payosResult(
            PaymentOrder order,
            vn.payos.model.v2.paymentRequests.PaymentLinkStatus status,
            long paid) {
        return vn.payos
                .model
                .v2
                .paymentRequests
                .PaymentLink
                .builder()
                .id("payos-test-link")
                .orderCode(987123L)
                .amount(order.amount)
                .amountPaid(paid)
                .amountRemaining(Math.max(0, order.amount - paid))
                .createdAt(Instant.now().toString())
                .transactions(java.util.List.of())
                .status(status)
                .build();
    }

    @Test
    void payosPartialTransferCannotIssueTicketsAndFullPaymentIsIdempotent() {
        var order = payosBooking();
        payments.settlePayos(
                order.id,
                payosResult(
                        order,
                        vn.payos.model.v2.paymentRequests.PaymentLinkStatus.UNDERPAID,
                        1000));
        assertEquals("PENDING", order.status);
        assertTrue(bookings.get(free().id, order.bookingId, false).tickets().isEmpty());
        var full =
                payosResult(
                        order,
                        vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PAID,
                        order.amount);
        payments.settlePayos(order.id, full);
        payments.settlePayos(order.id, full);
        assertEquals("PAID", order.status);
        assertEquals(1, bookings.get(free().id, order.bookingId, false).tickets().size());
        var receipt =
                db.queryForMap(
                        "select recipient,text_body,html_body from email_outbox where dedup_key=?",
                        "receipt:" + order.id);
        assertEquals(free().email, receipt.get("recipient"));
        String body = receipt.get("text_body").toString();
        assertTrue(body.contains(order.bookingId));
        assertTrue(body.contains("payOS"));
        assertTrue(body.contains("Suất chiếu:"));
        assertTrue(body.contains("Ghế:"));
        assertFalse(body.contains("MoMo Sandbox"));
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from email_outbox where dedup_key=?",
                        Long.class,
                        "receipt:" + order.id));
    }

    @Test
    void payosWrongAmountRejectedAndLatePaymentRequiresReview() {
        var order = payosBooking();
        var wrong =
                payosResult(
                        order,
                        vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PAID,
                        order.amount);
        wrong.setAmount(1L);
        assertThrows(ApiException.class, () -> payments.settlePayos(order.id, wrong));
        bookings.cancel(free().id, order.bookingId);
        payments.settlePayos(
                order.id,
                payosResult(
                        order,
                        vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PAID,
                        order.amount));
        assertEquals("PAYMENT_REVIEW", bookings.get(free().id, order.bookingId, false).status());
    }

    @Test
    void adminAndCustomerCookiesAreIndependent() throws Exception {
        String input =
                "{\"email\":\"admin@cineverse.local\",\"password\":\"TestOnly!2026\",\"remember\":false}";
        var adminResponse =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .header("Origin", "http://localhost:5174")
                                        .contentType("application/json")
                                        .content(input))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        assertNotNull(adminResponse.getCookie("cv_admin_refresh"));
        assertNull(adminResponse.getCookie("cv_refresh"));
        var userResponse =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .header("Origin", "http://localhost:5173")
                                        .contentType("application/json")
                                        .content(input.replace("admin@", "free@")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        var adminCookie = adminResponse.getCookie("cv_admin_refresh");
        var userCookie = userResponse.getCookie("cv_refresh");
        assertNotNull(userCookie);
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .header("Origin", "http://localhost:5174")
                                .cookie(adminCookie, userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("user.role").value("ADMIN"));
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .header("Origin", "http://localhost:5173")
                                .cookie(adminCookie, userCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("user.role").value("USER"));
    }

    User free() {
        return users.findByEmail("free@cineverse.local").orElseThrow();
    }

    User vip() {
        return users.findByEmail("vip@cineverse.local").orElseThrow();
    }

    User administrator() {
        return users.findByEmail("admin@cineverse.local").orElseThrow();
    }

    AuthDtos.Register registration(String email) {
        return new AuthDtos.Register("Khán giả kiểm thử", email, "StrongPass!2026", true, false);
    }

    String emailToken(String recipient, String template) {
        String body =
                db.queryForObject(
                        "select text_body from email_outbox where recipient=? and template=? order"
                                + " by id desc limit 1",
                        String.class,
                        recipient,
                        template);
        return Objects.requireNonNull(body).substring(body.lastIndexOf("token=") + 6).strip();
    }

    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .JwtRequestPostProcessor
            as(User u) {
        return jwt().jwt(
                        j ->
                                j.subject(u.id.toString())
                                        .claim("sv", u.sessionVersion)
                                        .claim("role", u.role))
                .authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_" + u.role));
    }

    @Test
    void migrationAndSeedHaveRealRelations() {
        assertEquals(40, movies.count());
        assertEquals(
                40, catalog.list(Map.of("type", "MOVIE", "size", "48"), false).totalElements());
        assertEquals(2, bookings.cinemas().size());
        assertEquals(320, db.queryForObject("select count(*) from seats", Long.class));
        assertEquals(3, users.count());
        assertEquals(0, db.queryForObject("select count(*) from payment_orders", Long.class));
    }

    @Test
    void registerHashesPasswordAndQueuesTwoEmails() {
        auth.register(registration("new@example.test"));
        User u = users.findByEmail("new@example.test").orElseThrow();
        assertFalse(u.verified);
        assertNotEquals("StrongPass!2026", u.passwordHash);
        assertTrue(u.passwordHash.startsWith("$2"));
        assertEquals(2, db.queryForObject("select count(*) from email_outbox", Long.class));
        assertEquals(
                0,
                db.queryForObject(
                        "select count(*) from email_outbox where template='VERIFY'", Long.class));
        assertEquals(
                0,
                db.queryForObject(
                        "select count(*) from auth_tokens where kind='VERIFY'", Long.class));
    }

    @Test
    void duplicateEmailRejected() {
        assertThrows(ApiException.class, () -> auth.register(registration(free().email)));
    }

    @Test
    void invalidRegistrationValidatedAtHttpBoundary() throws Exception {
        mvc.perform(
                        post("/api/v1/auth/register")
                                .contentType("application/json")
                                .content(
                                        "{\"name\":\"A\",\"email\":\"bad\",\"password\":\"weak\",\"terms\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("validationErrors.email").exists())
                .andExpect(jsonPath("validationErrors.password").exists());
    }

    @Test
    void loginRejectsWrongPasswordButAllowsUnverifiedAccount() {
        assertThrows(
                ApiException.class,
                () -> auth.login(new AuthDtos.Login(free().email, "wrong", false)));
        auth.register(registration("unverified@example.test"));
        assertNotNull(
                auth.login(
                        new AuthDtos.Login("unverified@example.test", "StrongPass!2026", false)));
    }

    @Test
    void unverifiedAccountCanRefreshAndAccessProtectedProfile() throws Exception {
        auth.register(registration("verify@example.test"));
        User u = users.findByEmail("verify@example.test").orElseThrow();
        assertFalse(u.verified);
        var session = auth.login(new AuthDtos.Login(u.email, "StrongPass!2026", false));
        assertNotNull(auth.refresh(session.refresh()));
        mvc.perform(get("/api/v1/users/me").with(as(u))).andExpect(status().isOk());
        auth.forgot(u.email);
        assertNotNull(emailToken(u.email, "RESET"));
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from email_outbox where template='WELCOME'", Long.class));
    }

    @Test
    void lockedAccountIsStillRejectedWithoutEmailVerification() throws Exception {
        auth.register(registration("expired@example.test"));
        User u = users.findByEmail("expired@example.test").orElseThrow();
        var session = auth.login(new AuthDtos.Login(u.email, "StrongPass!2026", false));
        u.locked = true;
        users.flush();
        assertThrows(
                ApiException.class,
                () -> auth.login(new AuthDtos.Login(u.email, "StrongPass!2026", false)));
        assertThrows(ApiException.class, () -> auth.refresh(session.refresh()));
        mvc.perform(get("/api/v1/users/me").with(as(u))).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesAndReplayRevokesSessions() {
        var s = auth.login(new AuthDtos.Login(free().email, "TestOnly!2026", false));
        var next = auth.refresh(s.refresh());
        assertNotEquals(s.refresh(), next.refresh());
        assertThrows(AuthService.RefreshReplay.class, () -> auth.refresh(s.refresh()));
        assertTrue(users.findById(free().id).orElseThrow().sessionVersion > 0);
    }

    @Test
    void passwordResetRevokesRefreshAndDoesNotRevealMissingEmail() {
        auth.forgot(free().email);
        auth.forgot("missing@example.test");
        String raw = emailToken(free().email, "RESET");
        auth.reset(new AuthDtos.Reset(raw, "ChangedPass!2026"));
        assertNotNull(auth.login(new AuthDtos.Login(free().email, "ChangedPass!2026", false)));
        assertThrows(
                ApiException.class, () -> auth.reset(new AuthDtos.Reset(raw, "OtherPass!2026")));
    }

    @Test
    void profileDoesNotExposePasswordHash() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(as(free())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("passwordHash").doesNotExist())
                .andExpect(jsonPath("email").value(free().email));
    }

    @Test
    void adminRejectsGuestAndUser() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/dashboard").with(as(free())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard").with(as(administrator())))
                .andExpect(status().isOk());
    }

    @Test
    void lockedUserAndOldSessionRejected() throws Exception {
        User u = free();
        u.locked = true;
        users.flush();
        mvc.perform(get("/api/v1/users/me").with(as(u))).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRequiresExactOrigin() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").header("Origin", "https://evil.test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchWithoutVietnameseAccentsAndPagination() {
        var result = catalog.list(Map.of("q", "phia ben kia chan troi", "size", "2"), false);
        assertEquals(1, result.totalElements());
        assertEquals("Phía bên kia chân trời", result.content().getFirst().title());
        assertEquals(5, catalog.list(Map.of("size", "5", "page", "2"), false).content().size());
    }

    @Test
    void combinedFiltersRunInDatabase() {
        var result =
                catalog.list(Map.of("type", "MOVIE", "genre", "hanh-dong", "size", "48"), false);
        assertFalse(result.content().isEmpty());
        assertTrue(
                result.content().stream()
                        .allMatch(
                                m ->
                                        m.type().equals("MOVIE")
                                                && m.genres().stream()
                                                        .anyMatch(
                                                                g ->
                                                                        g.slug()
                                                                                .equals(
                                                                                        "hanh-dong"))));
    }

    @Test
    void publishedMovieDetailIncludesSeasons() {
        var d = catalog.detail("hanh-trinh-vo-tan");
        assertEquals(2, d.seasons().size());
        assertEquals(4, d.seasons().getFirst().episodes().size());
    }

    @Test
    void favoriteIsIdempotent() {
        community.favorite(free().id, 2L);
        community.favorite(free().id, 2L);
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from favorites where user_id=? and movie_id=2",
                        Long.class,
                        free().id));
        community.unfavorite(free().id, 2L);
        assertTrue(community.favorites(free().id).stream().noneMatch(m -> m.id().equals(2L)));
    }

    @Test
    void progressPersistsAndCompletes() {
        community.progress(free().id, new CommunityService.Progress(1L, null, 96, 100));
        var h = community.history(free().id).getFirst();
        assertTrue(h.completed());
        assertEquals(96, h.positionSeconds());
        assertThrows(ApiException.class, () -> community.removeHistory(vip().id, h.id()));
    }

    @Test
    void progressRejectsEpisodeMismatchAndVipBypass() {
        movies.findById(5L).orElseThrow().vip = true;
        movies.flush();
        assertThrows(
                ApiException.class,
                () -> community.progress(free().id, new CommunityService.Progress(1L, 1L, 5, 100)));
        assertThrows(
                ApiException.class,
                () ->
                        community.progress(
                                free().id, new CommunityService.Progress(5L, null, 5, 100)));
    }

    @Test
    void reviewOwnershipAndUniqueness() {
        var r =
                community.review(
                        free().id, 9L, new CommunityService.ReviewInput(5, "Một góc nhìn mới."));
        community.review(free().id, 9L, new CommunityService.ReviewInput(4, "Đã cập nhật."));
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from reviews where movie_id=9 and user_id=?",
                        Long.class,
                        free().id));
        assertThrows(
                ApiException.class,
                () ->
                        community.editReview(
                                vip().id,
                                r.id(),
                                new CommunityService.ReviewInput(1, "Không được phép")));
    }

    @Test
    void vipAccessCheckedByBackendAndExpiry() {
        movies.findById(5L).orElseThrow().vip = true;
        movies.flush();
        assertThrows(ApiException.class, () -> streaming.movie(free(), 5L));
        assertNotNull(streaming.movie(vip(), 5L));
        User u = vip();
        u.vipUntil = Instant.now().minusSeconds(1);
        assertThrows(ApiException.class, () -> streaming.movie(u, 5L));
    }

    @Test
    void validAndInvalidRanges() throws Exception {
        var p = streaming.movie(free(), 1L);
        var result =
                mvc.perform(get(p.url()).header("Range", "bytes=0-9"))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        mvc.perform(asyncDispatch(result))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-9/1024"))
                .andExpect(content().bytes(new byte[10]));
        mvc.perform(get(p.url()).header("Range", "bytes=9999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */1024"));
    }

    @Test
    void mediaTicketDoesNotSurviveSessionRevocation() {
        var p = streaming.movie(free(), 1L);
        User u = free();
        u.sessionVersion++;
        users.flush();
        String ticket = p.url().split("/")[4];
        assertThrows(ApiException.class, () -> streaming.resolve(ticket, false));
    }

    @Test
    void paymentPriceComesFromDatabase() {
        var o = payments.create(free().id, 1L);
        assertEquals(49000, o.amount());
        assertEquals("SIMULATION", o.provider());
        assertEquals("PENDING", o.status());
        assertNull(free().vipUntil);
    }

    @Test
    void invalidPlanRejected() {
        assertThrows(ApiException.class, () -> payments.create(free().id, 9999L));
    }

    @Test
    void duplicateSimulationFulfillsExactlyOnce() {
        var o = payments.create(free().id, 1L);
        payments.simulate(free().id, o.id(), "success");
        Instant until = free().vipUntil;
        payments.simulate(free().id, o.id(), "success");
        assertEquals(until, free().vipUntil);
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from subscription_history where order_id=?",
                        Long.class,
                        o.id()));
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from email_outbox where dedup_key=?",
                        Long.class,
                        "receipt:" + o.id()));
    }

    @Test
    void renewExtendsExistingVip() {
        Instant old = vip().vipUntil;
        var o = payments.create(vip().id, 1L);
        payments.simulate(vip().id, o.id(), "success");
        assertEquals(old.plus(Duration.ofDays(30)), vip().vipUntil);
    }

    @Test
    void failureDoesNotActivateVipAndOtherUserCannotPay() {
        var o = payments.create(free().id, 1L);
        assertThrows(ApiException.class, () -> payments.simulate(vip().id, o.id(), "success"));
        payments.simulate(free().id, o.id(), "failure");
        assertNull(free().vipUntil);
        assertEquals("FAILED", orders.findById(o.id()).orElseThrow().status);
    }

    PaymentOrder momoOrder() {
        PaymentOrder o = new PaymentOrder();
        o.id = "CV-TEST-" + UUID.randomUUID();
        o.requestId = UUID.randomUUID().toString();
        o.userId = free().id;
        o.planId = 1L;
        o.planName = "VIP Tháng";
        o.durationDays = 30;
        o.amount = 49000;
        o.provider = "MOMO_SANDBOX";
        o.expiresAt = Instant.now().plusSeconds(1800);
        return orders.saveAndFlush(o);
    }

    Map<String, Object> ipn(PaymentOrder o, int code) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("partnerCode", "TEST_PARTNER");
        p.put("orderId", o.id);
        p.put("requestId", o.requestId);
        p.put("amount", o.amount);
        p.put("orderInfo", MomoSandboxPaymentGateway.info(o));
        p.put("orderType", "momo_wallet");
        p.put("payType", "qr");
        p.put("transId", 123456789);
        p.put("resultCode", code);
        p.put("responseTime", System.currentTimeMillis());
        p.put("extraData", "");
        p.put("message", "Successful.");
        sign(p);
        return p;
    }

    void sign(Map<String, Object> p) {
        var signed = new HashMap<>(p);
        signed.put("accessKey", "TEST_ACCESS");
        p.put(
                "signature",
                MomoSignature.sign(
                        "TEST_SECRET_32_CHARACTERS_FOR_TEST",
                        MomoSignature.raw(signed, MomoSignature.IPN)));
    }

    @Test
    void signedIpnAcceptsAndDuplicateSafe() {
        var o = momoOrder();
        var p = ipn(o, 0);
        payments.ipn(p);
        Instant until = free().vipUntil;
        payments.ipn(p);
        assertEquals("PAID", o.status);
        assertEquals(until, free().vipUntil);
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from subscription_history where order_id=?",
                        Long.class,
                        o.id));
    }

    @Test
    void invalidSignatureRejected() {
        var o = momoOrder();
        var p = ipn(o, 0);
        p.put("signature", "0".repeat(64));
        assertThrows(ApiException.class, () -> payments.ipn(p));
        assertEquals("PENDING", o.status);
    }

    @Test
    void signedWrongAmountAndRequestRejected() {
        var o = momoOrder();
        var p = ipn(o, 0);
        p.put("amount", 1);
        sign(p);
        assertThrows(ApiException.class, () -> payments.ipn(p));
        p.put("amount", o.amount);
        p.put("requestId", "other");
        sign(p);
        assertThrows(ApiException.class, () -> payments.ipn(p));
    }

    @Test
    void pending9000DoesNotActivateVip() {
        var o = momoOrder();
        payments.ipn(ipn(o, 9000));
        assertNull(free().vipUntil);
        assertEquals("PENDING", o.status);
    }

    @Test
    void paidOrderNeverDowngradesOnFailure() {
        var o = momoOrder();
        payments.ipn(ipn(o, 0));
        payments.ipn(ipn(o, 1006));
        assertEquals("PAID", o.status);
    }

    @Test
    void signatureMatchesStandardHmacVector() {
        assertEquals(
                "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                MomoSignature.sign("key", "The quick brown fox jumps over the lazy dog"));
        assertFalse(MomoSignature.matches("a".repeat(64), null));
    }

    @Test
    void lastAdminCannotBeRemoved() {
        assertThrows(
                ApiException.class,
                () ->
                        admin.user(
                                administrator().id,
                                new AdminService.UserAction("USER", false, "test"),
                                administrator().id));
    }

    @Test
    void emailFailureKeepsPaidAndRetries() {
        var o = payments.create(free().id, 1L);
        payments.simulate(free().id, o.id(), "success");
        when(mail.createMimeMessage())
                .thenThrow(
                        new org.springframework.mail.MailSendException("test transport failure"));
        outbox.deliver();
        assertEquals("PAID", orders.findById(o.id()).orElseThrow().status);
        assertEquals(
                2,
                db.queryForObject(
                        "select count(*) from email_outbox where status='PENDING' and attempts=1",
                        Long.class));
    }

    @Test
    void unsafeUploadAndPathTraversalRejected() {
        var f =
                new MockMultipartFile(
                        "file", "attack.mp4", "video/mp4", "<script>bad</script>".getBytes());
        assertThrows(ApiException.class, () -> uploads.upload(f, false, administrator().id));
        assertThrows(ApiException.class, () -> uploads.asset("../secrets"));
    }

    private Long futureShow() {
        return db.queryForObject(
                "select id from showtimes where starts_at>? order by starts_at limit 1",
                Long.class,
                java.sql.Timestamp.from(Instant.now().plusSeconds(3600)));
    }

    private com.cineverse.booking.BookingDtos.BookingView holdSeats(int count) {
        Long show = futureShow();
        var seats =
                bookings.seats(show).seats().stream()
                        .filter(s -> s.status().equals("AVAILABLE"))
                        .limit(count)
                        .map(com.cineverse.booking.BookingDtos.SeatView::id)
                        .toList();
        return bookings.hold(
                free().id, new com.cineverse.booking.BookingDtos.HoldInput(show, seats));
    }

    @Test
    void seatHoldUsesServerPricesAndCannotBeDuplicated() {
        var b = holdSeats(2);
        assertEquals("HELD", b.status());
        assertEquals(
                b.items().stream()
                        .mapToLong(com.cineverse.booking.BookingDtos.ItemView::price)
                        .sum(),
                b.totalAmount());
        assertEquals(78, bookings.seats(b.showtime().id()).showtime().availableSeats());
        assertThrows(
                ApiException.class,
                () ->
                        bookings.hold(
                                vip().id,
                                new com.cineverse.booking.BookingDtos.HoldInput(
                                        b.showtime().id(),
                                        List.of(b.items().getFirst().seatId()))));
    }

    @Test
    void seatMustBelongToSelectedRoom() {
        Long show = futureShow();
        var s = bookings.showtime(show);
        Long foreign =
                db.queryForObject(
                        "select id from seats where auditorium_id<>? limit 1",
                        Long.class,
                        s.auditoriumId());
        assertThrows(
                ApiException.class,
                () ->
                        bookings.hold(
                                free().id,
                                new com.cineverse.booking.BookingDtos.HoldInput(
                                        show, List.of(foreign))));
    }

    @Test
    void holdExpiresAndReleasesSeats() {
        var b = holdSeats(1);
        db.update(
                "update bookings set hold_until=? where id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
                b.id());
        bookings.expire();
        assertEquals("EXPIRED", bookings.get(free().id, b.id(), false).status());
        assertEquals(
                0,
                db.queryForObject(
                        "select count(*) from seat_reservations where booking_id=?",
                        Long.class,
                        b.id()));
    }

    @Test
    void bookingOwnershipAndCancellation() {
        var b = holdSeats(1);
        assertThrows(ApiException.class, () -> bookings.get(vip().id, b.id(), false));
        assertThrows(ApiException.class, () -> bookings.cancel(vip().id, b.id()));
        bookings.cancel(free().id, b.id());
        assertEquals("CANCELLED", bookings.get(free().id, b.id(), false).status());
        assertEquals(
                0,
                db.queryForObject(
                        "select count(*) from seat_reservations where booking_id=?",
                        Long.class,
                        b.id()));
    }

    @Test
    void terminalPaymentFailureReleasesSeatsAndLateSuccessNeedsReview() {
        for (String outcome : List.of("failure", "cancel")) {
            var b = holdSeats(1);
            var o = payments.createForBooking(free().id, b.id());
            payments.simulate(free().id, o.id(), outcome);
            assertEquals("CANCELLED", bookings.get(free().id, b.id(), false).status());
            assertEquals(
                    0L,
                    db.queryForObject(
                            "select count(*) from seat_reservations where booking_id=?",
                            Long.class,
                            b.id()));
            var persisted = orders.findById(o.id()).orElseThrow();
            persisted.provider = "MOMO_SANDBOX";
            orders.flush();
            var callback = ipn(persisted, 0);
            callback.put("transId", outcome.equals("failure") ? 123456781 : 123456782);
            sign(callback);
            payments.ipn(callback);
            var late = bookings.get(free().id, b.id(), false);
            assertEquals("PAYMENT_REVIEW", late.status());
            assertTrue(late.tickets().isEmpty());
        }
    }

    @Test
    void paidBookingIssuesTicketsOnceAndQueuesReceipt() {
        var b = holdSeats(2);
        var o = payments.createForBooking(free().id, b.id());
        assertEquals(b.totalAmount(), o.amount());
        payments.simulate(free().id, o.id(), "success");
        payments.simulate(free().id, o.id(), "success");
        var confirmed = bookings.get(free().id, b.id(), false);
        assertEquals("CONFIRMED", confirmed.status());
        assertEquals(2, confirmed.tickets().size());
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from email_outbox where dedup_key=?",
                        Long.class,
                        "receipt:" + o.id()));
        assertThrows(ApiException.class, () -> bookings.cancel(free().id, b.id()));
    }

    @Test
    void vipSeatSurchargeIsCalculatedOnServer() {
        Long show = futureShow();
        var map = bookings.seats(show);
        var vipSeat =
                map.seats().stream().filter(s -> s.kind().equals("VIP")).findFirst().orElseThrow();
        var b =
                bookings.hold(
                        free().id,
                        new com.cineverse.booking.BookingDtos.HoldInput(
                                show, List.of(vipSeat.id())));
        assertEquals(map.showtime().basePrice() + 20000, b.totalAmount());
    }

    @Test
    void lateSignedPaymentEntersReviewWithoutTakingAnotherSeat() {
        var b = holdSeats(1);
        var created = payments.createForBooking(free().id, b.id());
        var o = orders.findById(created.id()).orElseThrow();
        o.provider = "MOMO_SANDBOX";
        orders.flush();
        db.update(
                "update bookings set hold_until=? where id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
                b.id());
        bookings.expire();
        var another =
                bookings.hold(
                        vip().id,
                        new com.cineverse.booking.BookingDtos.HoldInput(
                                b.showtime().id(), List.of(b.items().getFirst().seatId())));
        payments.ipn(ipn(o, 0));
        assertEquals("PAID", o.status);
        assertEquals("PAYMENT_REVIEW", bookings.get(free().id, b.id(), false).status());
        assertTrue(bookings.get(free().id, b.id(), false).tickets().isEmpty());
        assertEquals("HELD", bookings.get(vip().id, another.id(), false).status());
    }

    @Test
    void ticketCheckinIsSingleUse() {
        var b = holdSeats(1);
        var o = payments.createForBooking(free().id, b.id());
        payments.simulate(free().id, o.id(), "success");
        String code = bookings.get(free().id, b.id(), false).tickets().getFirst().code();
        assertEquals("USED", bookings.checkIn(code, administrator().id).status());
        assertThrows(ApiException.class, () -> bookings.checkIn(code, administrator().id));
    }

    @Test
    void overlappingShowtimesRejected() {
        var show = bookings.showtime(futureShow());
        assertThrows(
                ApiException.class,
                () ->
                        cinemaAdmin.showtime(
                                null,
                                new com.cineverse.booking.CinemaAdminService.ShowtimeInput(
                                        show.movieId(),
                                        show.auditoriumId(),
                                        show.startsAt(),
                                        75000,
                                        true),
                                administrator().id));
    }

    @Test
    void checkoutIsIdempotentForOneBooking() {
        var b = holdSeats(1);
        var first = payments.createForBooking(free().id, b.id());
        var second = payments.createForBooking(free().id, b.id());
        assertEquals(first.id(), second.id());
        assertEquals(
                1,
                db.queryForObject(
                        "select count(*) from payment_orders where booking_id=?",
                        Long.class,
                        b.id()));
    }

    @Test
    void publicScheduleAndProtectedHolds() throws Exception {
        mvc.perform(get("/api/v1/cinemas")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/showtimes")).andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/bookings")
                                .contentType("application/json")
                                .content("{\"showtimeId\":1,\"seatIds\":[1]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/admin/tickets/check-in")
                                .with(as(free()))
                                .contentType("application/json")
                                .content("{\"code\":\"nope\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changingEmailRequiresPasswordAndConfirmation() {
        var u = free();
        assertThrows(
                ApiException.class,
                () ->
                        auth.requestEmailChange(
                                u.id, new AuthDtos.EmailChange("changed@example.test", "wrong")));
        auth.requestEmailChange(
                u.id, new AuthDtos.EmailChange("changed@example.test", "TestOnly!2026"));
        assertEquals("free@cineverse.local", u.email);
        String raw = emailToken("changed@example.test", "EMAIL_CHANGE");
        auth.confirmEmailChange(raw);
        assertEquals("changed@example.test", u.email);
        assertTrue(u.sessionVersion > 0);
        assertThrows(ApiException.class, () -> auth.confirmEmailChange(raw));
    }

    @Test
    void catalogCreateUpdateSoftDeleteAudited() {
        var input = catalog.edit(1L);
        var created =
                new CatalogDtos.MovieInput(
                        "Kiểm thử",
                        "Test",
                        "kiem-thu",
                        input.synopsis(),
                        input.shortDescription(),
                        "MOVIE",
                        input.countryId(),
                        2026,
                        100,
                        "T13",
                        "Tiếng Việt",
                        "Đạo diễn",
                        "Diễn viên",
                        "demo",
                        input.poster(),
                        input.backdrop(),
                        "DRAFT",
                        false,
                        false,
                        0,
                        input.genreIds(),
                        "sintel.mp4",
                        "sintel.mp4",
                        null);
        var saved = catalog.save(null, created, administrator().id);
        assertEquals(0, catalog.list(Map.of("q", "kiem-thu"), false).totalElements());
        catalog.delete(saved.id(), administrator().id);
        assertTrue(movies.findById(saved.id()).orElseThrow().deleted);
        assertEquals(
                2,
                db.queryForObject(
                        "select count(*) from audit_logs where entity_type='MOVIE' and entity_id=?",
                        Long.class,
                        saved.id().toString()));
    }
}
