package finos.traderx.tradeservice;

import finos.traderx.messaging.Publisher;
import finos.traderx.tradeservice.model.TradeOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "trade.security.jwt.jwk-set-uri=https://example.invalid/jwks")
@AutoConfigureMockMvc
class TradeOrderControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private Publisher<TradeOrder> publisher;

    @MockBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    void tradeWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/trade/").contentType("application/json").content(""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tradeWithBodyWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/trade/").contentType("application/json").content("{\"security\":\"IBM\",\"accountId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tradeWithInvalidTokenIsUnauthorized() throws Exception {
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("Invalid signature"));
        mvc.perform(post("/trade/").header("Authorization", "Bearer invalid")
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tradeWithValidTokenReachesRequestValidation() throws Exception {
        when(jwtDecoder.decode("valid")).thenReturn(Jwt.withTokenValue("valid")
                .header("alg", "RS256").subject("trader").build());
        mvc.perform(post("/trade/").header("Authorization", "Bearer valid")
                .contentType("application/json").content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiDocsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void unlistedRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/private")).andExpect(status().isUnauthorized());
    }

    @Test
    void corsPreflightDoesNotRequireToken() throws Exception {
        mvc.perform(options("/trade/").header("Origin", "https://example.com")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk());
    }
}
