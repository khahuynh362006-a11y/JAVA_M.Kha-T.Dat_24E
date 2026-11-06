package com.cineverse.payments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cineverse.shared.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;

class VnpaySandboxPaymentGatewayTest {
    private static final String SECRET = "UNIT_TEST_VNPAY_SECRET_AT_LEAST_32";
    private final HttpClient http = mock(HttpClient.class);
    private final ObjectMapper json = new ObjectMapper();

    private VnpaySandboxPaymentGateway gateway() {
        return new VnpaySandboxPaymentGateway(
                "TEST_TMN",
                SECRET,
                "http://localhost:8080/api/v1/payments/vnpay/return",
                "127.0.0.1",
                json,
                http);
    }

    private PaymentOrder order() {
        var order = new PaymentOrder();
        order.id = "CV-vnp-test";
        order.amount = 95000;
        order.createdAt = Instant.parse("2026-11-05T03:00:00Z");
        order.expiresAt = Instant.now().plusSeconds(480);
        return order;
    }

    private Map<String, String> params(String url) {
        var result = new HashMap<String, String>();
        for (var pair : URI.create(url).getRawQuery().split("&")) {
            var parts = pair.split("=", 2);
            result.put(
                    URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        return result;
    }

    @Test
    void createsSignedSandboxUrlWithServerPriceTimes100AndVietnamTime() {
        var url = gateway().create(order());
        assertEquals("sandbox.vnpayment.vn", URI.create(url).getHost());
        var values = params(url);
        assertEquals("9500000", values.get("vnp_Amount"));
        assertEquals("20261105100000", values.get("vnp_CreateDate"));
        assertEquals("2.1.0", values.get("vnp_Version"));
        assertTrue(gateway().validNotification(values));
        assertFalse(url.contains(SECRET));
    }

    @Test
    void signatureUsesSortedFormEncoding() {
        var values = Map.of("vnp_Z", "A B+C", "vnp_A", "1", "vnp_SecureHash", "ignored");
        assertEquals("vnp_A=1&vnp_Z=A+B%2BC", VnpaySignature.canonical(values));
        assertEquals(
                "b42af09057bac1e2d41708e48a902e09b5ff7f12ab428a4fe86653c73dd248fb82f948a549f7b791a5b41915ee4d1ec3935357e4e2317250d0372afa2ebeeb3a",
                VnpaySignature.sign("key", "The quick brown fox jumps over the lazy dog"));
    }

    @Test
    void rejectsTamperedAmountAndMerchant() {
        var values = params(gateway().create(order()));
        values.put("vnp_Amount", "1");
        assertFalse(gateway().validNotification(values));
        values = params(gateway().create(order()));
        values.put("vnp_TmnCode", "OTHER");
        assertFalse(gateway().validNotification(values));
    }

    @Test
    void configurationAndMinimumAmountFailClosed() {
        var unavailable =
                new VnpaySandboxPaymentGateway(
                        "",
                        "",
                        "http://localhost:8080/api/v1/payments/vnpay/return",
                        "127.0.0.1",
                        json,
                        http);
        assertFalse(unavailable.configured());
        assertThrows(ApiException.class, () -> unavailable.create(order()));
        var order = order();
        order.amount = 1000;
        assertThrows(ApiException.class, () -> gateway().create(order));
        verifyNoInteractions(http);
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryRequiresValidHmacAndMatchingOrder() throws Exception {
        Map<String, String> response =
                new HashMap<>(
                        Map.of(
                                "vnp_ResponseId",
                                "response-test",
                                "vnp_Command",
                                "querydr",
                                "vnp_ResponseCode",
                                "00",
                                "vnp_TmnCode",
                                "TEST_TMN",
                                "vnp_TxnRef",
                                "CV-vnp-test",
                                "vnp_Amount",
                                "9500000",
                                "vnp_TransactionStatus",
                                "00",
                                "vnp_TransactionNo",
                                "12345",
                                "vnp_OrderInfo",
                                "CineVerse - CV-vnp-test"));
        response.put(
                "vnp_SecureHash",
                VnpaySignature.sign(
                        SECRET,
                        VnpaySignature.pipe(response, VnpaySandboxPaymentGateway.QUERY_RESPONSE)));
        HttpResponse<String> reply = mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(200);
        when(reply.body()).thenReturn(json.writeValueAsString(response));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(
                        call -> {
                            HttpRequest request = call.getArgument(0);
                            assertEquals(
                                    "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                                    request.uri().toString());
                            return reply;
                        });
        assertEquals("00", gateway().query(order()).get("vnp_TransactionStatus"));
        response.put("vnp_Amount", "1");
        when(reply.body()).thenReturn(json.writeValueAsString(response));
        assertThrows(ApiException.class, () -> gateway().query(order()));
    }
}
