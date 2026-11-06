package com.cineverse.payments;

import com.cineverse.shared.ApiException;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import vn.payos.model.v2.paymentRequests.*;
import vn.payos.model.webhooks.WebhookData;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Component
public class PayosPaymentGateway implements PaymentGateway {
    private final boolean ready;
    private final String frontend;
    private final JdbcTemplate db;
    private final PayOS client;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public PayosPaymentGateway(
            @Value("${app.payments.payos.enabled:false}") boolean enabled,
            @Value("${app.payments.payos.client-id:}") String clientId,
            @Value("${app.payments.payos.api-key:}") String apiKey,
            @Value("${app.payments.payos.checksum-key:}") String checksumKey,
            @Value("${app.frontend-url}") String frontend,
            JdbcTemplate db) {
        this.ready =
                enabled
                        && validKey(clientId)
                        && validKey(apiKey)
                        && validKey(checksumKey)
                        && validFrontend(frontend);
        this.frontend = frontend.replaceAll("/+$", "");
        this.db = db;
        this.client =
                ready
                        ? new PayOS(
                                ClientOptions.builder()
                                        .clientId(clientId)
                                        .apiKey(apiKey)
                                        .checksumKey(checksumKey)
                                        .baseURL("https://api-merchant.payos.vn")
                                        .timeoutMs(15000)
                                        .maxRetries(0)
                                        .logLevel(ClientOptions.LogLevel.NONE)
                                        .build())
                        : null;
    }

    PayosPaymentGateway(PayOS client, String frontend, JdbcTemplate db) {
        this.client = client;
        this.frontend = frontend;
        this.db = db;
        this.ready = client != null;
    }

    private static boolean validKey(String value) {
        return value != null && value.matches("[\\x21-\\x7e]{16,512}");
    }

    private static boolean validFrontend(String value) {
        try {
            var uri = URI.create(value);
            return uri.getHost() != null
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && (uri.getScheme().equals("https")
                            || (uri.getScheme().equals("http")
                                    && Set.of("localhost", "127.0.0.1", "[::1]")
                                            .contains(uri.getHost())));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String provider() {
        return "PAYOS";
    }

    public boolean configured() {
        return ready;
    }

    private void requireReady() {
        if (!ready)
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYOS_NOT_CONFIGURED",
                    "payOS chưa được kết nối. Vui lòng liên hệ quản trị viên.");
    }

    private long ensureCode(PaymentOrder order) {
        long code = random.nextLong(1, 9_007_199_254_740_991L);
        db.update(
                "insert into payos_links(order_code,order_id,created_at) select ?,?,? where not"
                    + " exists (select 1 from payos_links where order_id=?)",
                code,
                order.id,
                Timestamp.from(Instant.now()),
                order.id);
        return code(order.id);
    }

    public long code(String id) {
        return Objects.requireNonNull(
                db.queryForObject(
                        "select order_code from payos_links where order_id=?", Long.class, id));
    }

    public String orderId(long code) {
        var result =
                db.queryForList(
                        "select order_id from payos_links where order_code=?", String.class, code);
        return result.isEmpty() ? null : result.getFirst();
    }

    public String create(PaymentOrder order) {
        requireReady();
        long code = ensureCode(order);
        String result =
                frontend
                        + "/thanh-toan/ket-qua?orderId="
                        + URLEncoder.encode(order.id, StandardCharsets.UTF_8);
        var request =
                CreatePaymentLinkRequest.builder()
                        .orderCode(code)
                        .amount(order.amount)
                        .description("CV" + (code % 10_000_000))
                        .returnUrl(result)
                        .cancelUrl(result + "&cancel=1")
                        .expiredAt(order.expiresAt.getEpochSecond())
                        .build();
        try {
            var link = client.paymentRequests().create(request);
            if (!Objects.equals(link.getOrderCode(), code)
                    || !Objects.equals(link.getAmount(), order.amount)
                    || !"VND".equals(link.getCurrency())
                    || link.getPaymentLinkId() == null)
                throw ApiException.bad("Thông tin payOS không khớp đơn hàng.");
            var uri = URI.create(link.getCheckoutUrl());
            if (!"https".equals(uri.getScheme())
                    || !"pay.payos.vn".equals(uri.getHost())
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443))
                throw ApiException.bad("Liên kết thanh toán không thuộc payOS.");
            db.update(
                    "update payos_links set payment_link_id=? where order_id=?",
                    link.getPaymentLinkId(),
                    order.id);
            return link.getCheckoutUrl();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PAYOS_CREATE_UNCERTAIN",
                    "Chưa xác nhận được việc tạo giao dịch payOS. Hệ thống sẽ đối soát; không thanh"
                        + " toán lặp.");
        }
    }

    public PaymentLink query(PaymentOrder order) {
        requireReady();
        long code = code(order.id);
        try {
            var result = client.paymentRequests().get(code);
            if (!Objects.equals(result.getOrderCode(), code)
                    || !Objects.equals(result.getAmount(), order.amount)
                    || result.getId() == null
                    || result.getStatus() == null)
                throw ApiException.bad("Kết quả payOS không khớp đơn hàng.");
            var expected =
                    db.queryForObject(
                            "select payment_link_id from payos_links where order_id=?",
                            String.class,
                            order.id);
            if (expected != null && !expected.equals(result.getId()))
                throw ApiException.bad("Mã link payOS không khớp.");
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PAYOS_QUERY_FAILED",
                    "Chưa đối soát được payOS. Vui lòng kiểm tra lại.");
        }
    }

    public WebhookData verifyWebhook(Map<String, Object> payload) {
        requireReady();
        try {
            return client.webhooks().verify(payload);
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SIGNATURE",
                    "Chữ ký webhook payOS không hợp lệ.");
        }
    }

    @PreDestroy
    public void close() {
        if (client != null) client.close();
    }
}
