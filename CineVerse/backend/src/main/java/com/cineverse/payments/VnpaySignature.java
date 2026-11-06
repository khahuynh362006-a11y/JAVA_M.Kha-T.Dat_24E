package com.cineverse.payments;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VnpaySignature {
    private VnpaySignature() {}

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String canonical(Map<String, String> values) {
        return new TreeMap<>(values)
                .entrySet().stream()
                        .filter(
                                e ->
                                        e.getKey().startsWith("vnp_")
                                                && !Set.of("vnp_SecureHash", "vnp_SecureHashType")
                                                        .contains(e.getKey())
                                                && e.getValue() != null
                                                && !e.getValue().isEmpty())
                        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                        .collect(java.util.stream.Collectors.joining("&"));
    }

    public static String sign(String secret, String text) {
        try {
            var mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign VNPAY request", e);
        }
    }

    public static boolean matches(String expected, String actual) {
        return actual != null
                && actual.matches("[0-9a-fA-F]{128}")
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.US_ASCII),
                        actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    public static String pipe(Map<String, String> values, String... keys) {
        return Arrays.stream(keys)
                .map(k -> values.getOrDefault(k, ""))
                .collect(java.util.stream.Collectors.joining("|"));
    }
}
