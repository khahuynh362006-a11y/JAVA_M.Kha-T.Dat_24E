package com.cineverse.payments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cineverse.shared.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.function.Function;

class MomoSandboxPaymentGatewayTest {
    private static final String PARTNER = "MERCHANT_TEST",
            ACCESS = "ACCESS_TEST",
            SECRET = "SECRET_FOR_UNIT_TEST_ONLY_32_CHARS";
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);

    private MomoSandboxPaymentGateway gateway(String ipn) {
        return new MomoSandboxPaymentGateway(
                PARTNER,
                ACCESS,
                SECRET,
                "http://localhost:5173/thanh-toan/ket-qua",
                ipn,
                json,
                http);
    }

    private MomoSandboxPaymentGateway gateway() {
        return gateway("https://merchant.example/api/v1/payments/momo/ipn");
    }

    private PaymentOrder order() {
        var order = new PaymentOrder();
        order.id = "CV-test-1";
        order.requestId = "request-1";
        order.amount = 95000;
        return order;
    }

    private Map<String, Object> requestBody(HttpRequest request) throws Exception {
        var result = new CompletableFuture<String>();
        var bytes = new java.io.ByteArrayOutputStream();
        request.bodyPublisher()
                .orElseThrow()
                .subscribe(
                        new Flow.Subscriber<ByteBuffer>() {
                            public void onSubscribe(Flow.Subscription subscription) {
                                subscription.request(Long.MAX_VALUE);
                            }

                            public void onNext(ByteBuffer buffer) {
                                byte[] part = new byte[buffer.remaining()];
                                buffer.get(part);
                                bytes.writeBytes(part);
                            }

                            public void onError(Throwable error) {
                                result.completeExceptionally(error);
                            }

                            public void onComplete() {
                                result.complete(bytes.toString(StandardCharsets.UTF_8));
                            }
                        });
        return json.readValue(result.get(2, TimeUnit.SECONDS), new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private void respond(Function<Map<String, Object>, Map<String, Object>> response)
            throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(
                        call -> {
                            HttpRequest request = call.getArgument(0);
                            assertEquals("test-payment.momo.vn", request.uri().getHost());
                            assertEquals("https", request.uri().getScheme());
                            HttpResponse<String> reply = mock(HttpResponse.class);
                            when(reply.statusCode()).thenReturn(200);
                            when(reply.body())
                                    .thenReturn(
                                            json.writeValueAsString(
                                                    response.apply(requestBody(request))));
                            return reply;
                        });
    }

    private Map<String, Object> created(Map<String, Object> request) {
        return new HashMap<>(
                Map.of(
                        "partnerCode",
                        PARTNER,
                        "requestId",
                        request.get("requestId"),
                        "orderId",
                        request.get("orderId"),
                        "resultCode",
                        0,
                        "payUrl",
                        "https://test-payment.momo.vn/v2/gateway/pay?t=unit-test"));
    }

    @Test
    void createSignsServerAmountAndUsesSandboxOnly() throws Exception {
        respond(
                request -> {
                    assertEquals("captureWallet", request.get("requestType"));
                    assertEquals(95000, request.get("amount"));
                    assertEquals(true, request.get("autoCapture"));
                    assertFalse(request.containsKey("secretKey"));
                    assertFalse(request.containsKey("accessKey"));
                    var signing = new HashMap<>(request);
                    signing.put("accessKey", ACCESS);
                    assertEquals(
                            MomoSignature.sign(
                                    SECRET, MomoSignature.raw(signing, MomoSignature.CREATE)),
                            request.get("signature"));
                    return created(request);
                });
        assertTrue(gateway().create(order()).startsWith("https://test-payment.momo.vn/"));
    }

    @Test
    void noCredentialsOrPublicIpnMeansNoNetworkCall() {
        for (String ipn :
                List.of(
                        "",
                        "http://merchant.example/api/v1/payments/momo/ipn",
                        "https://localhost/api/v1/payments/momo/ipn",
                        "https://merchant.example/wrong-path")) {
            var gateway = gateway(ipn);
            assertFalse(gateway.configured());
            assertEquals(
                    "MOMO_NOT_CONFIGURED",
                    assertThrows(ApiException.class, () -> gateway.create(order())).code);
        }
        verifyNoInteractions(http);
    }

    @Test
    void rejectsResponseForAnotherOrder() throws Exception {
        respond(
                request -> {
                    var response = created(request);
                    response.put("orderId", "another-order");
                    return response;
                });
        assertThrows(ApiException.class, () -> gateway().create(order()));
    }

    @Test
    void rejectsResponseForAnotherRequest() throws Exception {
        respond(
                request -> {
                    var response = created(request);
                    response.put("requestId", "another-request");
                    return response;
                });
        assertThrows(ApiException.class, () -> gateway().create(order()));
    }

    @Test
    void rejectsProductionAndForeignPayUrls() throws Exception {
        for (String url :
                List.of(
                        "https://payment.momo.vn/pay",
                        "https://test-payment.momo.vn.evil.example/pay",
                        "http://test-payment.momo.vn/pay",
                        "https://test-payment.momo.vn:8443/pay")) {
            respond(
                    request -> {
                        var response = created(request);
                        response.put("payUrl", url);
                        return response;
                    });
            assertThrows(ApiException.class, () -> gateway().create(order()));
        }
    }

    @Test
    void merchantRejectionIsDistinguishedFromNetworkUncertainty() throws Exception {
        respond(request -> Map.of("resultCode", 4001));
        assertEquals(
                "MOMO_REJECTED",
                assertThrows(ApiException.class, () -> gateway().create(order())).code);
    }

    @Test
    void timeoutRemainsUncertain() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("unit test"));
        assertEquals(
                "GATEWAY_UNAVAILABLE",
                assertThrows(ApiException.class, () -> gateway().create(order())).code);
    }

    @Test
    void querySignsAndBindsResponseToItsOwnRequest() throws Exception {
        respond(
                request -> {
                    var signing = new HashMap<>(request);
                    signing.put("accessKey", ACCESS);
                    assertEquals(
                            MomoSignature.sign(
                                    SECRET, MomoSignature.raw(signing, MomoSignature.QUERY)),
                            request.get("signature"));
                    return Map.of(
                            "partnerCode",
                            PARTNER,
                            "orderId",
                            request.get("orderId"),
                            "requestId",
                            request.get("requestId"),
                            "resultCode",
                            1000);
                });
        assertEquals(1000, gateway().query(order()).get("resultCode"));
    }

    @Test
    void queryRejectsMismatchedResponse() throws Exception {
        respond(
                request ->
                        Map.of(
                                "partnerCode",
                                PARTNER,
                                "orderId",
                                "wrong",
                                "requestId",
                                request.get("requestId"),
                                "resultCode",
                                0));
        assertThrows(ApiException.class, () -> gateway().query(order()));
    }
}
