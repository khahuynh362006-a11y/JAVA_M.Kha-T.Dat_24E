package com.cineverse.payments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cineverse.shared.ApiException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.*;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import java.time.Instant;
import java.util.*;

class PayosPaymentGatewayTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final PayOS client = mock(PayOS.class);
    private final PaymentRequestsService requests = mock(PaymentRequestsService.class);
    private final PayosPaymentGateway gateway =
            new PayosPaymentGateway(client, "http://localhost:5173", db);

    private PaymentOrder order() {
        var order = new PaymentOrder();
        order.id = "CVtestPayos";
        order.amount = 95000;
        order.expiresAt = Instant.now().plusSeconds(480);
        return order;
    }

    private void prepare() {
        when(db.queryForObject(anyString(), eq(Long.class), any())).thenReturn(123456L);
        when(client.paymentRequests()).thenReturn(requests);
    }

    private CreatePaymentLinkResponse response() {
        return CreatePaymentLinkResponse.builder()
                .bin("970422")
                .accountNumber("0000000000")
                .accountName("TEST ONLY")
                .description("CV123456")
                .qrCode("TEST_QR_ONLY")
                .orderCode(123456L)
                .amount(95000L)
                .currency("VND")
                .paymentLinkId("link123")
                .checkoutUrl("https://pay.payos.vn/web/link123")
                .status(PaymentLinkStatus.PENDING)
                .build();
    }

    @Test
    void disabledGatewayCannotTouchDatabaseOrCreateMoneyRequest() {
        var disabled =
                new PayosPaymentGateway(
                        false,
                        "fake-client-123456",
                        "fake-api-123456789",
                        "fake-checksum-123456",
                        "http://localhost:5173",
                        db);
        assertFalse(disabled.configured());
        assertEquals(
                "PAYOS_NOT_CONFIGURED",
                assertThrows(ApiException.class, () -> disabled.create(order())).code);
        verifyNoInteractions(db, client);
    }

    @Test
    void createUsesServerAmountAndPrivateOrderCode() {
        prepare();
        when(requests.create(any(CreatePaymentLinkRequest.class))).thenReturn(response());
        assertTrue(gateway.create(order()).startsWith("https://pay.payos.vn/"));
        var captor = ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
        verify(requests).create(captor.capture());
        assertEquals(95000L, captor.getValue().getAmount());
        assertEquals(123456L, captor.getValue().getOrderCode());
        assertTrue(captor.getValue().getReturnUrl().contains("orderId=CVtestPayos"));
        assertTrue(captor.getValue().getDescription().length() <= 9);
    }

    @Test
    void rejectsForeignCheckoutAndWrongAmount() {
        prepare();
        var response = response();
        response.setCheckoutUrl("https://pay.payos.vn.evil.example/web/link123");
        when(requests.create(any(CreatePaymentLinkRequest.class))).thenReturn(response);
        assertThrows(ApiException.class, () -> gateway.create(order()));
        response.setCheckoutUrl("https://pay.payos.vn/web/link123");
        response.setAmount(1L);
        assertThrows(ApiException.class, () -> gateway.create(order()));
    }

    @Test
    void queryChecksLinkAndAmount() {
        prepare();
        when(db.queryForObject(anyString(), eq(String.class), any())).thenReturn("link123");
        var result =
                PaymentLink.builder()
                        .id("link123")
                        .orderCode(123456L)
                        .amount(95000L)
                        .amountPaid(95000L)
                        .amountRemaining(0L)
                        .createdAt(Instant.now().toString())
                        .transactions(List.of())
                        .status(PaymentLinkStatus.PAID)
                        .build();
        when(requests.get(123456L)).thenReturn(result);
        assertEquals(PaymentLinkStatus.PAID, gateway.query(order()).getStatus());
        result.setId("different");
        assertThrows(ApiException.class, () -> gateway.query(order()));
    }

    @Test
    void officialSdkValidatesWebhookHmacWithoutNetwork() {
        String checksum = "UNIT_TEST_CHECKSUM_ONLY_32_CHARS";
        var sdk = new PayOS("unit-test-client-id", "unit-test-api-key", checksum);
        var verifier = new PayosPaymentGateway(sdk, "http://localhost:5173", db);
        Map<String, Object> data =
                new TreeMap<>(
                        Map.of(
                                "orderCode",
                                123,
                                "amount",
                                3000,
                                "description",
                                "CV123",
                                "currency",
                                "VND",
                                "paymentLinkId",
                                "link-test",
                                "code",
                                "00",
                                "reference",
                                "test-reference"));
        data.put("accountNumber", "0000000000");
        data.put("transactionDateTime", "2026-11-05 12:00:00");
        data.put("desc", "success");
        for (String key :
                List.of(
                        "counterAccountBankId",
                        "counterAccountBankName",
                        "counterAccountName",
                        "counterAccountNumber",
                        "virtualAccountName",
                        "virtualAccountNumber")) data.put(key, "");
        String canonical =
                data.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(java.util.stream.Collectors.joining("&"));
        Map<String, Object> payload =
                new HashMap<>(
                        Map.of(
                                "code",
                                "00",
                                "desc",
                                "success",
                                "success",
                                true,
                                "data",
                                data,
                                "signature",
                                MomoSignature.sign(checksum, canonical)));
        assertEquals(123L, verifier.verifyWebhook(payload).getOrderCode());
        data.put("amount", 1);
        assertThrows(ApiException.class, () -> verifier.verifyWebhook(payload));
        verifier.close();
    }
}
