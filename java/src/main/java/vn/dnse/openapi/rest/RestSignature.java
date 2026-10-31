package vn.dnse.openapi.rest;

/** Result of signing one DNSE REST request. */
public record RestSignature(
        String dateHeaderName,
        String dateValue,
        String nonce,
        String headersList,
        String signingString,
        String encodedSignature,
        String authorizationValue
) {}
