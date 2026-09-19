package vn.dnse.openapi.rest;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RestAuthSignerTest {
    @Test
    void matchesPythonHmacSha256GoldenVector() {
        DnseRestConfig config = DnseRestConfig.builder()
                .apiKey("test-key")
                .apiSecret("test-secret")
                .clock(Clock.fixed(
                        Instant.parse("2026-07-23T10:11:12Z"),
                        ZoneOffset.UTC
                ))
                .nonceGenerator(() -> "0123456789abcdef0123456789abcdef")
                .build();

        RestSignature signature = new RestAuthSigner(config)
                .sign("GET", "/market/instruments");

        assertEquals("Date", signature.dateHeaderName());
        assertEquals("Thu, 23 Jul 2026 10:11:12 +0000", signature.dateValue());
        assertEquals("0123456789abcdef0123456789abcdef", signature.nonce());
        assertEquals("(request-target) date", signature.headersList());
        assertEquals(
                "(request-target): get /market/instruments\n"
                        + "date: Thu, 23 Jul 2026 10:11:12 +0000\n"
                        + "nonce: 0123456789abcdef0123456789abcdef",
                signature.signingString()
        );
        assertEquals(
                "t%2FcEoFNqZ6dUA2pV6N3yCwCE87QGMHdEI0sWMQ%2Bj4PE%3D",
                signature.encodedSignature()
        );
        assertEquals(
                "Signature keyId=\"test-key\",algorithm=\"hmac-sha256\","
                        + "headers=\"(request-target) date\","
                        + "signature=\"t%2FcEoFNqZ6dUA2pV6N3yCwCE87QGMHdEI0sWMQ%2Bj4PE%3D\","
                        + "nonce=\"0123456789abcdef0123456789abcdef\"",
                signature.authorizationValue()
        );
    }

    @Test
    void omitsNonceWhenDisabled() {
        DnseRestConfig config = DnseRestConfig.builder()
                .apiKey("test-key")
                .apiSecret("test-secret")
                .hmacNonceEnabled(false)
                .clock(Clock.fixed(
                        Instant.parse("2026-07-23T10:11:12Z"),
                        ZoneOffset.UTC
                ))
                .build();

        RestSignature signature = new RestAuthSigner(config)
                .sign("GET", "/market/working-dates");

        assertNull(signature.nonce());
        assertEquals(
                "(request-target): get /market/working-dates\n"
                        + "date: Thu, 23 Jul 2026 10:11:12 +0000",
                signature.signingString()
        );
    }
}
