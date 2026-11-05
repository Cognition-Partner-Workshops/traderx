package finos.traderx.positionservice;

import finos.traderx.positionservice.controller.TradeController;
import finos.traderx.positionservice.controller.PositionController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:fs09_positions;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=file:../database/initialSchema.sql"
})
@AutoConfigureMockMvc
class ClientRecordsTest {
    private String previousSecret;

    @BeforeEach
    void configureTestReferenceSecret() {
        previousSecret = System.getProperty("traderx.reference.secret");
        System.setProperty("traderx.reference.secret", UUID.randomUUID().toString());
    }

    @AfterEach
    void restoreReferenceSecret() {
        if (previousSecret == null) System.clearProperty("traderx.reference.secret");
        else System.setProperty("traderx.reference.secret", previousSecret);
    }

    @Autowired MockMvc http;

    @Test
    void tradesAndPositionsDoNotDiscloseAccountNumbersIncludingLegacyTradeIds() throws Exception {
        for (String path : new String[]{"/trades/22214", "/positions/22214", "/trades/", "/positions/"}) {
            String body = http.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("22214"), "Account number leaked by " + path);
            assertTrue(body.contains("****2214"));
        }
    }

    @Test
    void emptyResultsAndErrorsAreSafe() throws Exception {
        http.perform(get("/trades/99999999")).andExpect(status().isOk()).andExpect(content().json("[]"));
        assertFalse(new TradeController().generalError(new Exception("22214")).getBody().contains("22214"));
        assertFalse(new PositionController().generalError(new Exception("22214")).getBody().contains("22214"));
    }
}
