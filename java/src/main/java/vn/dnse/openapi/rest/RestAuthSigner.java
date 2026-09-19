package vn.dnse.openapi.rest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds DNSE REST HTTP-signature headers with behavior matching python/dnse/api/common.py.
 *
 * <p>The signed request target contains only the HTTP method and path. Query parameters are not
 * part of the signature in the current Python SDK.</p>
 */
public final class RestAuthSigner {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
                    .withZone(ZoneOffset.UTC);

    private final DnseRestConfig config;

    public RestAuthSigner(DnseRestConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public RestSignature sign(String method, String path) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");

        Instant now = Instant.now(config.clock());
        String dateValue = DATE_FORMATTER.format(now);
        String nonce = config.hmacNonceEnabled() ? config.nonceGenerator().next() : null;
        String headerKey = config.dateHeaderName().toLowerCase(Locale.ROOT);
        String headersList = "(request-target) " + headerKey;

        StringBuilder signing = new StringBuilder()
                .append("(request-target): ")
                .append(method.toLowerCase(Locale.ROOT))
                .append(' ')
                .append(path)
                .append('\n')
                .append(headerKey)
                .append(": ")
                .append(dateValue);

        if (nonce != null) {
            signing.append("\nnonce: ").append(nonce);
        }

        String encodedSignature = signAndEncode(
                config.apiSecret(),
                signing.toString(),
                config.algorithm()
        );

        StringBuilder authorization = new StringBuilder()
                .append("Signature keyId=\"")
                .append(config.apiKey())
                .append("\",algorithm=\"")
                .append(config.algorithm())
                .append("\",headers=\"")
                .append(headersList)
                .append("\",signature=\"")
                .append(encodedSignature)
                .append('\"');

        if (nonce != null) {
            authorization.append(",nonce=\"").append(nonce).append('\"');
        }

        return new RestSignature(
                config.dateHeaderName(),
                dateValue,
                nonce,
                headersList,
                signing.toString(),
                encodedSignature,
                authorization.toString()
        );
    }

    static String signAndEncode(String secret, String signingString, String algorithm) {
        try {
            String jcaAlgorithm = switch (algorithm.toLowerCase(Locale.ROOT)) {
                case "hmac-sha256" -> "HmacSHA256";
                case "hmac-sha384" -> "HmacSHA384";
                case "hmac-sha512" -> "HmacSHA512";
                default -> "HmacSHA1";
            };

            Mac mac = Mac.getInstance(jcaAlgorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), jcaAlgorithm));
            String base64 = Base64.getEncoder().encodeToString(
                    mac.doFinal(signingString.getBytes(StandardCharsets.UTF_8))
            );
            return URLEncoder.encode(base64, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new DnseRestException("Unable to sign DNSE REST request", e);
        }
    }
}
