package finos.traderx.positionservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"traderx.auth.secret=" + TestTokens.SECRET,
		"spring.datasource.url=jdbc:h2:mem:positionservice-test;DB_CLOSE_DELAY=-1",
		"spring.datasource.driverClassName=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=sa",
		"spring.jpa.hibernate.ddl-auto=create",
})
@AutoConfigureMockMvc
class PositionEndpointSecurityTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void anonymousCallsAreRejected() throws Exception {
		mockMvc.perform(get("/positions/")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/positions/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/trades/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void apiDocsAreExplicitlyPublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void accountDataIsOnlyVisibleToOwnersAndPrivilegedCallers() throws Exception {
		jdbcTemplate.update("MERGE INTO ACCOUNTUSERS (AccountID, Username) KEY (AccountID, Username) VALUES (301, 'user01')");

		for (String path : new String[] { "/positions/301", "/trades/301" }) {
			mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("user01", "USER")))
				.andExpect(status().isOk());
			mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("user02", "USER")))
				.andExpect(status().isForbidden());
			mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("admin", "ADMIN")))
				.andExpect(status().isOk());
		}
		mockMvc.perform(get("/positions/").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("user01", "USER")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/trades/").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("trade-processor", "SERVICE")))
			.andExpect(status().isOk());
	}
}
