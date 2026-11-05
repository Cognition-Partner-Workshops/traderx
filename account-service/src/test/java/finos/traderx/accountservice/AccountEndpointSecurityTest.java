package finos.traderx.accountservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import finos.traderx.accountservice.model.Account;
import finos.traderx.accountservice.model.AccountUser;
import finos.traderx.accountservice.repository.AccountRepository;
import finos.traderx.accountservice.repository.AccountUserRepository;

@SpringBootTest(properties = {
		"traderx.auth.secret=" + TestTokens.SECRET,
		"spring.datasource.url=jdbc:h2:mem:accountservice-test;DB_CLOSE_DELAY=-1",
		"spring.datasource.driverClassName=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=sa",
		"spring.jpa.hibernate.ddl-auto=create",
})
@AutoConfigureMockMvc
class AccountEndpointSecurityTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accountRepository;

	@Autowired
	AccountUserRepository accountUserRepository;

	private static Account account(int id, String displayName) {
		Account account = new Account();
		account.setId(id);
		account.setDisplayName(displayName);
		return account;
	}

	@Test
	void anonymousCallsAreRejected() throws Exception {
		mockMvc.perform(get("/account/")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/account/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/accountuser/")).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() throws Exception {
		String forged = "Bearer " + TestTokens.token("user01", "another-secret-that-is-also-32-bytes-long", "ADMIN");
		mockMvc.perform(get("/account/").header(HttpHeaders.AUTHORIZATION, forged))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void apiDocsAreExplicitlyPublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void userOnlySeesOwnAccounts() throws Exception {
		int ownedId = accountRepository.save(account(101, "Owned")).getId();
		int otherId = accountRepository.save(account(102, "Someone else's")).getId();
		AccountUser mapping = new AccountUser();
		mapping.setAccountId(ownedId);
		mapping.setUsername("user01");
		accountUserRepository.save(mapping);

		mockMvc.perform(get("/account/").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("user01", "USER")))
			.andExpect(status().isOk())
			.andExpect(content().json("[{\"id\":" + ownedId + ",\"displayName\":\"Owned\"}]"));
		mockMvc.perform(get("/account/" + ownedId).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("user01", "USER")))
			.andExpect(status().isOk());
		mockMvc.perform(get("/account/" + otherId).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("user01", "USER")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/account/" + otherId).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("trade-service", "SERVICE")))
			.andExpect(status().isOk());
	}

	@Test
	void onlyAdminsMayWriteAccountsAndMappings() throws Exception {
		String body = "{\"id\":201,\"displayName\":\"New\"}";
		mockMvc.perform(post("/account/").contentType(MediaType.APPLICATION_JSON).content(body)
				.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("user01", "USER")))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/account/").contentType(MediaType.APPLICATION_JSON).content(body)
				.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("admin", "ADMIN")))
			.andExpect(status().isOk());
		mockMvc.perform(get("/accountuser/").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("user01", "USER")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/accountuser/").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("admin", "ADMIN")))
			.andExpect(status().isOk());
	}
}
