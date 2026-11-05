package finos.traderx.tradeprocessor;

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

import finos.traderx.tradeprocessor.security.ServiceTokenIssuer;

@SpringBootTest(properties = {
		"traderx.auth.secret=" + TestTokens.SECRET,
		"spring.datasource.url=jdbc:h2:mem:tradeprocessor-test;DB_CLOSE_DELAY=-1",
		"spring.datasource.driverClassName=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=sa",
		"spring.jpa.hibernate.ddl-auto=create",
})
@AutoConfigureMockMvc
class TradeBookingEndpointSecurityTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ServiceTokenIssuer serviceTokenIssuer;

	@Test
	void anonymousBookingIsRejected() throws Exception {
		mockMvc.perform(post("/tradeservice/order").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void ordinaryUsersMayNotBookDirectly() throws Exception {
		mockMvc.perform(post("/tradeservice/order").contentType(MediaType.APPLICATION_JSON).content("{}")
				.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("user01", "USER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void serviceTokensMintedBySelfAreAccepted() throws Exception {
		mockMvc.perform(post("/tradeservice/order").contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":\"T-1\",\"accountId\":1,\"security\":\"MSFT\",\"quantity\":1,\"side\":\"Buy\",\"state\":\"New\"}")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenIssuer.issueToken()))
			.andExpect(status().isOk());
	}

	@Test
	void apiDocsAreExplicitlyPublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}
}
