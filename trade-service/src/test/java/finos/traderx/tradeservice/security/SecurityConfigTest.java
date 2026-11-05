package finos.traderx.tradeservice.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {
    @Test
    void missingJwkSetUriFailsClosed() {
        for (String uri : new String[] {"", "  "}) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> new SecurityConfig().jwtDecoder(uri, ""));
            assertTrue(error.getMessage().contains("TRADE_SERVICE_JWT_JWK_SET_URI"));
        }
    }
}
