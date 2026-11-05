package finos.traderx.tradeservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"traderx.auth.secret=" + TestTokens.SECRET,
})
@AutoConfigureMockMvc
class TradeOrderEndpointSecurityTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void anonymousTradeSubmissionIsRejected() throws Exception {
		mockMvc.perform(post("/trade/").contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountId\":1,\"security\":\"MSFT\",\"quantity\":1,\"side\":\"Buy\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() throws Exception {
		String forged = "Bearer " + TestTokens.token("user01", "another-secret-that-is-also-32-bytes-long", "USER");
		mockMvc.perform(post("/trade/").contentType(MediaType.APPLICATION_JSON).content("{}")
				.header(HttpHeaders.AUTHORIZATION, forged))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void apiDocsAreExplicitlyPublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}
}
