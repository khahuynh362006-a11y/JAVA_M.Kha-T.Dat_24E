package com.cineverse.payments;

import com.cineverse.shared.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class VnpaySandboxPaymentGateway implements PaymentGateway {
    private static final String PAY = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
            QUERY = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    static final String[] QUERY_RESPONSE = {
        "vnp_ResponseId",
        "vnp_Command",
        "vnp_ResponseCode",
        "vnp_Message",
        "vnp_TmnCode",
        "vnp_TxnRef",
        "vnp_Amount",
        "vnp_BankCode",
        "vnp_PayDate",
        "vnp_TransactionNo",
        "vnp_TransactionType",
        "vnp_TransactionStatus",
        "vnp_OrderInfo",
        "vnp_PromotionCode",
        "vnp_PromotionAmount"
    };
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final String terminal, secret, returnUrl, clientIp;
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    public VnpaySandboxPaymentGateway(
            @Value("${app.payments.vnpay.tmn-code:}") String terminal,
            @Value("${app.payments.vnpay.hash-secret:}") String secret,
            @Value(
                            "${app.payments.vnpay.return-url:http://localhost:8080/api/v1/payments/vnpay/return}")
                    String returnUrl,
            @Value("${app.payments.vnpay.client-ip:127.0.0.1}") String clientIp,
            ObjectMapper json) {
        this(
                terminal,
                secret,
                returnUrl,
                clientIp,
                json,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    VnpaySandboxPaymentGateway(
            String terminal,
            String secret,
            String returnUrl,
            String clientIp,
            ObjectMapper json,
            HttpClient http) {
        this.terminal = terminal;
        this.secret = secret;
        this.returnUrl = returnUrl;
        this.clientIp = clientIp;
        this.json = json;
        this.http = http;
    }

    public String provider() {
        return "VNPAY_SANDBOX";
    }

    public boolean configured() {
        try {
            var url = URI.create(returnUrl);
            return !terminal.isBlank()
                    && secret.length() >= 16
                    && url.getHost() != null
                    && url.getUserInfo() == null
                    && url.getFragment() == null
                    && url.getQuery() == null
                    && "/api/v1/payments/vnpay/return".equals(url.getPath())
                    && (url.getScheme().equals("https")
                            || (url.getScheme().equals("http")
                                    && Set.of("localhost", "127.0.0.1", "[::1]")
                                            .contains(url.getHost())));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String create(PaymentOrder order) {
        if (!configured())
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "VNPAY_NOT_CONFIGURED",
                    "VNPAY Sandbox cần TmnCode và HashSecret do VNPAY cấp.");
        if (order.amount < 5000
                || order.expiresAt == null
                || !order.expiresAt.isAfter(Instant.now()))
            throw ApiException.bad("VNPAY yêu cầu tối thiểu 5.000đ và đơn còn thời hạn.");
        Map<String, String> data = new TreeMap<>();
        data.put("vnp_Version", "2.1.0");
        data.put("vnp_Command", "pay");
        data.put("vnp_TmnCode", terminal);
        data.put("vnp_Amount", Long.toString(Math.multiplyExact(order.amount, 100)));
        data.put("vnp_CurrCode", "VND");
        data.put("vnp_TxnRef", order.id);
        data.put("vnp_OrderInfo", info(order));
        data.put("vnp_OrderType", "other");
        data.put("vnp_Locale", "vn");
        data.put("vnp_ReturnUrl", returnUrl);
        data.put("vnp_IpAddr", clientIp);
        data.put("vnp_CreateDate", DATE.format(order.createdAt));
        data.put("vnp_ExpireDate", DATE.format(order.expiresAt));
        String query = VnpaySignature.canonical(data);
        return PAY + "?" + query + "&vnp_SecureHash=" + VnpaySignature.sign(secret, query);
    }

    public boolean validNotification(Map<String, String> data) {
        return configured()
                && terminal.equals(data.get("vnp_TmnCode"))
                && VnpaySignature.matches(
                        VnpaySignature.sign(secret, VnpaySignature.canonical(data)),
                        data.get("vnp_SecureHash"));
    }

    public Map<String, String> query(PaymentOrder order) {
        if (!configured()) throw ApiException.bad("VNPAY Sandbox chưa được cấu hình.");
        Map<String, String> request = new LinkedHashMap<>();
        request.put("vnp_RequestId", UUID.randomUUID().toString().replace("-", ""));
        request.put("vnp_Version", "2.1.0");
        request.put("vnp_Command", "querydr");
        request.put("vnp_TmnCode", terminal);
        request.put("vnp_TxnRef", order.id);
        request.put("vnp_TransactionDate", DATE.format(order.createdAt));
        request.put("vnp_CreateDate", DATE.format(Instant.now()));
        request.put("vnp_IpAddr", clientIp);
        request.put("vnp_OrderInfo", info(order));
        request.put(
                "vnp_SecureHash",
                VnpaySignature.sign(
                        secret,
                        VnpaySignature.pipe(
                                request,
                                "vnp_RequestId",
                                "vnp_Version",
                                "vnp_Command",
                                "vnp_TmnCode",
                                "vnp_TxnRef",
                                "vnp_TransactionDate",
                                "vnp_CreateDate",
                                "vnp_IpAddr",
                                "vnp_OrderInfo")));
        try {
            var response =
                    http.send(
                            HttpRequest.newBuilder(URI.create(QUERY))
                                    .timeout(Duration.ofSeconds(15))
                                    .header("Content-Type", "application/json")
                                    .POST(
                                            HttpRequest.BodyPublishers.ofString(
                                                    json.writeValueAsString(request)))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new IllegalStateException("Gateway unavailable");
            Map<String, String> result = json.readValue(response.body(), new TypeReference<>() {});
            if (!terminal.equals(result.get("vnp_TmnCode"))
                    || !order.id.equals(result.get("vnp_TxnRef"))
                    || !"querydr".equals(result.get("vnp_Command"))
                    || !VnpaySignature.matches(
                            VnpaySignature.sign(
                                    secret, VnpaySignature.pipe(result, QUERY_RESPONSE)),
                            result.get("vnp_SecureHash")))
                throw ApiException.bad("Kết quả đối soát VNPAY không hợp lệ.");
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GATEWAY_UNAVAILABLE",
                    "Chưa thể đối soát VNPAY. Vui lòng kiểm tra lại.");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GATEWAY_UNAVAILABLE",
                    "Chưa thể đối soát VNPAY. Vui lòng kiểm tra lại.");
        }
    }

    public static String info(PaymentOrder order) {
        return "CineVerse - " + order.id;
    }
}
