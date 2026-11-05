package finos.traderx.tradeservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {
    @Test
    void missingJwkSetUriFailsClosed() {
        for (String uri : new String[] {"", "  "}) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> new SecurityConfig().jwtDecoder(uri, "", ""));
            assertTrue(error.getMessage().contains("TRADE_SERVICE_JWT_JWK_SET_URI"));
        }
    }

    @Test
    void configuredAudienceMustBePresent() {
        var validator = new SecurityConfig().jwtValidator("", "trade-service");
        assertFalse(validator.validate(token(List.of("other-app", "trade-service"))).hasErrors());
        assertTrue(validator.validate(token(List.of("other-app"))).hasErrors());
        assertTrue(validator.validate(token(List.of())).hasErrors());
        assertTrue(validator.validate(token(null)).hasErrors());
    }

    @Test
    void emptyAudienceDoesNotRestrictTokens() {
        assertFalse(new SecurityConfig().jwtValidator("", "").validate(token(null)).hasErrors());
    }

    @Test
    void audienceValidationPreservesIssuerAndTimestampValidation() {
        var validator = new SecurityConfig().jwtValidator("https://issuer.example", "trade-service");
        Jwt matching = token(List.of("trade-service"));
        assertFalse(validator.validate(matching).hasErrors());
        assertTrue(validator.validate(Jwt.withTokenValue("token").headers(headers -> headers.putAll(matching.getHeaders()))
                .claims(claims -> claims.putAll(matching.getClaims())).issuer("https://other.example").build()).hasErrors());
        assertTrue(validator.validate(Jwt.withTokenValue("token").headers(headers -> headers.putAll(matching.getHeaders()))
                .claims(claims -> claims.putAll(matching.getClaims())).expiresAt(Instant.now().minusSeconds(300)).build()).hasErrors());
    }

    private Jwt token(List<String> audiences) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("trader")
                .issuer("https://issuer.example").audience(audiences)
                .expiresAt(Instant.now().plusSeconds(300)).build();
    }
}
