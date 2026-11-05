package finos.traderx.positionservice.service;

import finos.traderx.positionservice.model.Trade;
import finos.traderx.positionservice.repository.TradeRepository;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:settlement-history;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class SettlementHistoryTests {
    @Autowired TradeRepository trades;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    @ParameterizedTest
    @CsvSource({"2026-11-03,Processing", "2026-11-06,Processing", "2026-11-09,Processing", "2026-11-09,Settled"})
    void returnsStoredDateAndStateOnBothHistoryEndpoints(String date, String state) throws Exception {
        Trade trade = new Trade();
        trade.setId("history-test");
        trade.setAccountId(22214);
        trade.setSettlementDate(LocalDate.parse(date));
        trade.setState(state);
        trade.setQuantity(10);
        trade.setSecurity("IBM");
        trade.setSide("Buy");
        trades.saveAndFlush(trade);
        em.clear();
        for (String path : new String[]{"/trades/22214", "/trades/"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("history-test"))
                .andExpect(jsonPath("$[0].settlementDate").value(date))
                .andExpect(jsonPath("$[0].state").value(state));
        }
    }
}
