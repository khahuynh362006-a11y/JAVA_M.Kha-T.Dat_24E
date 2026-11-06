package com.cineverse.payments;

import com.cineverse.shared.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
public class MomoSandboxPaymentGateway implements PaymentGateway {
    private static final String ENDPOINT = "https://test-payment.momo.vn/v2/gateway/api/";
    private final String partner, access, secret, redirect, ipn;
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    public MomoSandboxPaymentGateway(
            @Value("${app.payments.momo.partner-code}") String partner,
            @Value("${app.payments.momo.access-key}") String access,
            @Value("${app.payments.momo.secret-key}") String secret,
            @Value("${app.payments.momo.redirect-url}") String redirect,
            @Value("${app.payments.momo.ipn-url}") String ipn,
            ObjectMapper json) {
        this(
                partner,
                access,
                secret,
                redirect,
                ipn,
                json,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    MomoSandboxPaymentGateway(
            String partner,
            String access,
            String secret,
            String redirect,
            String ipn,
            ObjectMapper json,
            HttpClient http) {
        this.partner = partner;
        this.access = access;
        this.secret = secret;
        this.redirect = redirect;
        this.ipn = ipn;
        this.json = json;
        this.http = http;
    }

    public String provider() {
        return "MOMO_SANDBOX";
    }

    public boolean configured() {
        return !partner.isBlank()
                && !access.isBlank()
                && secret.length() >= 16
                && callbackUrl(ipn, true)
                && callbackUrl(redirect, false);
    }

    private static boolean callbackUrl(String value, boolean notification) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getFragment() != null)
                return false;
            boolean local =
                    host.equalsIgnoreCase("localhost")
                            || host.startsWith("127.")
                            || host.equals("[::1]");
            if (notification)
                return !local
                        && "https".equalsIgnoreCase(uri.getScheme())
                        && "/api/v1/payments/momo/ipn".equals(uri.getPath())
                        && uri.getQuery() == null;
            return "https".equalsIgnoreCase(uri.getScheme())
                    || (local && "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String create(PaymentOrder o) {
        if (!configured())
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MOMO_NOT_CONFIGURED",
                    "MoMo Sandbox chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", partner);
        body.put("accessKey", access);
        body.put("requestId", o.requestId);
        body.put("amount", o.amount);
        body.put("orderId", o.id);
        body.put("orderInfo", info(o));
        body.put("redirectUrl", redirect);
        body.put("ipnUrl", ipn);
        body.put("lang", "vi");
        body.put("extraData", "");
        body.put("requestType", "captureWallet");
        body.put("autoCapture", true);
        body.put(
                "signature",
                MomoSignature.sign(secret, MomoSignature.raw(body, MomoSignature.CREATE)));
        body.remove("accessKey");
        Map<String, Object> response = post("create", body);
        if (code(response) != 0)
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "MOMO_REJECTED",
                    "MoMo Sandbox từ chối tạo giao dịch (mã "
                            + code(response)
                            + "). Vui lòng kiểm tra cấu hình merchant.");
        if (!partner.equals(response.get("partnerCode"))
                || !o.id.equals(response.get("orderId"))
                || !o.requestId.equals(response.get("requestId")))
            throw ApiException.bad("Kết quả tạo thanh toán không khớp đơn hàng.");
        String payUrl = Objects.toString(response.get("payUrl"), "");
        URI uri;
        try {
            uri = URI.create(payUrl);
        } catch (Exception e) {
            throw ApiException.bad("MoMo trả về liên kết không hợp lệ.");
        }
        if (!"https".equals(uri.getScheme())
                || !"test-payment.momo.vn".equals(uri.getHost())
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443))
            throw ApiException.bad("Liên kết thanh toán không thuộc MoMo Sandbox.");
        return payUrl;
    }

    public void verify(Map<String, Object> event) {
        if (!configured())
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MOMO_NOT_CONFIGURED",
                    "MoMo chưa được cấu hình.");
        Map<String, Object> signed = new HashMap<>(event);
        signed.put("accessKey", access);
        if (!partner.equals(event.get("partnerCode"))
                || !MomoSignature.matches(
                        MomoSignature.sign(secret, MomoSignature.raw(signed, MomoSignature.IPN)),
                        Objects.toString(event.get("signature"), null)))
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Chữ ký thanh toán không hợp lệ.");
    }

    public Map<String, Object> query(PaymentOrder o) {
        if (!configured()) throw ApiException.bad("MoMo chưa được cấu hình.");
        Map<String, Object> body = new HashMap<>();
        body.put("accessKey", access);
        body.put("partnerCode", partner);
        body.put("orderId", o.id);
        body.put("requestId", UUID.randomUUID().toString());
        body.put("lang", "vi");
        body.put(
                "signature",
                MomoSignature.sign(secret, MomoSignature.raw(body, MomoSignature.QUERY)));
        body.remove("accessKey");
        Map<String, Object> result = post("query", body);
        // Query responses have no signature field in the official contract. Authenticate
        // the fixed HTTPS server and bind the response to this signed request instead.
        if (!partner.equals(result.get("partnerCode"))
                || !o.id.equals(result.get("orderId"))
                || !body.get("requestId").equals(result.get("requestId")))
            throw ApiException.bad("Kết quả đối soát không khớp yêu cầu.");
        return result;
    }

    private Map<String, Object> post(String operation, Map<String, Object> body) {
        try {
            var request =
                    HttpRequest.newBuilder(URI.create(ENDPOINT + operation))
                            .timeout(Duration.ofSeconds(35))
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            json.writeValueAsString(body)))
                            .build();
            var result = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (result.statusCode() != 200)
                throw new IllegalStateException("Unexpected gateway HTTP status");
            return json.readValue(result.body(), new TypeReference<>() {});
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GATEWAY_UNAVAILABLE",
                    "Không thể liên hệ cổng thanh toán.");
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GATEWAY_UNAVAILABLE",
                    "Không thể liên hệ cổng thanh toán. Đơn sẽ được đối soát.");
        }
    }

    public static String info(PaymentOrder o) {
        return "CineVerse - " + o.id;
    }

    public static int code(Map<String, Object> e) {
        try {
            return Integer.parseInt(Objects.toString(e.get("resultCode")));
        } catch (Exception x) {
            throw ApiException.bad("Mã kết quả không hợp lệ.");
        }
    }
}
