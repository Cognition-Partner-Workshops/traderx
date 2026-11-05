package finos.traderx.accountservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import finos.traderx.accountservice.controller.AccountController;
import finos.traderx.accountservice.controller.AccountUserController;
import finos.traderx.accountservice.model.AccountUser;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:fs09_accounts;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=file:../database/initialSchema.sql"
})
@AutoConfigureMockMvc
class ClientRecordsTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;

    @Test
    void restUsesOpaqueReferencesAndOnlyLastFourDigitsWithoutBreakingLookupOrUpdate() throws Exception {
        String body = http.perform(get("/account/22214")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode account = json.readTree(body);
        assertEquals("****2214", account.path("accountNumber").asText());
        String reference = account.path("id").asText();
        assertTrue(reference.matches("[a-f0-9-]{36}"), "Account id must be an opaque reference, not the account number");
        assertFalse(body.contains("22214"));
        http.perform(get("/account/" + reference)).andExpect(status().isOk()).andExpect(jsonPath("$.accountNumber").value("****2214"));
        http.perform(put("/account/").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + reference + "\",\"displayName\":\"FS09 Account\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(reference));
        String users = http.perform(get("/accountuser/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(users.contains("22214"));
        assertTrue(users.contains("****2214"));
    }

    @Test
    void missingAccountIsMaskedAndNullAccountNumberStaysNull() throws Exception {
        http.perform(get("/account/99999999")).andExpect(status().isNotFound())
                .andExpect(content().string("Account with id ****9999 not found"));
        JsonNode user = json.valueToTree(new AccountUser());
        assertTrue(user.has("accountNumber"));
        assertTrue(user.get("accountNumber").isNull());
    }

    @Test
    void errorsDoNotEchoAccountNumbers() {
        assertFalse(new AccountController().generalError(new Exception("Account 22214 failed")).getBody().contains("22214"));
        assertFalse(new AccountUserController().generalError(new Exception("Account 22214 failed")).getBody().contains("22214"));
    }
}
