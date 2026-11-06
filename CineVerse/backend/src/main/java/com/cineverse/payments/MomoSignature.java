package com.cineverse.payments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class MomoSignature {
    private MomoSignature() {}

    public static String raw(Map<String, ?> values, String... keys) {
        return String.join(
                "&",
                Arrays.stream(keys)
                        .map(k -> k + "=" + Objects.toString(values.get(k), ""))
                        .toList());
    }

    public static String sign(String secret, String raw) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign payment request", e);
        }
    }

    public static boolean matches(String expected, String actual) {
        if (actual == null || !actual.matches("[0-9a-fA-F]{64}")) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    public static final String[] CREATE = {
        "accessKey",
        "amount",
        "extraData",
        "ipnUrl",
        "orderId",
        "orderInfo",
        "partnerCode",
        "redirectUrl",
        "requestId",
        "requestType"
    };
    public static final String[] IPN = {
        "accessKey",
        "amount",
        "extraData",
        "message",
        "orderId",
        "orderInfo",
        "orderType",
        "partnerCode",
        "payType",
        "requestId",
        "responseTime",
        "resultCode",
        "transId"
    };
    public static final String[] QUERY = {"accessKey", "orderId", "partnerCode", "requestId"};
}
