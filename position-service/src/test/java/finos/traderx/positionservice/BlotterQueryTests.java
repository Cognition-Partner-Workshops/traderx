package finos.traderx.positionservice;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import finos.traderx.positionservice.model.Position;
import finos.traderx.positionservice.model.Trade;
import finos.traderx.positionservice.repository.PositionRepository;
import finos.traderx.positionservice.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:position-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BlotterQueryTests {

    @Autowired MockMvc mvc;
    @Autowired PositionRepository positions;
    @Autowired TradeRepository trades;

    @BeforeEach
    void resetDatabase() {
        trades.deleteAll();
        positions.deleteAll();
    }

    @Test
    void positionsForAccountExcludeOtherAccountsAndPreserveQuantities() throws Exception {
        savePosition(101, "AAPL", 7);
        savePosition(101, "MSFT", -2);
        savePosition(202, "AAPL", 99);

        mvc.perform(get("/positions/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.security == 'AAPL')].quantity", contains(7)))
                .andExpect(jsonPath("$[?(@.security == 'MSFT')].quantity", contains(-2)));
        mvc.perform(get("/positions/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void allPositionsIncludeEveryAccount() throws Exception {
        savePosition(101, "AAPL", 7);
        savePosition(202, "MSFT", 11);

        mvc.perform(get("/positions/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.accountId == 101)].quantity", contains(7)))
                .andExpect(jsonPath("$[?(@.accountId == 202)].quantity", contains(11)));
    }

    @Test
    void tradesForAccountExcludeOtherAccountsAndPreserveBlotterFields() throws Exception {
        saveTrade("buy-101", 101, "AAPL", "BUY", "EXECUTED", 5);
        saveTrade("sell-101", 101, "MSFT", "SELL", "PENDING", 3);
        saveTrade("other-202", 202, "AAPL", "BUY", "EXECUTED", 99);

        mvc.perform(get("/trades/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == 'buy-101')].quantity", contains(5)))
                .andExpect(jsonPath("$[?(@.id == 'sell-101')].side", contains("SELL")))
                .andExpect(jsonPath("$[?(@.id == 'sell-101')].state", contains("PENDING")));
        mvc.perform(get("/trades/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void allTradesIncludeEveryAccount() throws Exception {
        saveTrade("first", 101, "AAPL", "BUY", "EXECUTED", 5);
        saveTrade("second", 202, "MSFT", "SELL", "PENDING", 9);

        mvc.perform(get("/trades/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == 'first')].accountId", contains(101)))
                .andExpect(jsonPath("$[?(@.id == 'second')].quantity", contains(9)));
    }

    private void savePosition(int accountId, String security, int quantity) {
        Position position = new Position();
        position.setAccountId(accountId);
        position.setSecurity(security);
        position.setQuantity(quantity);
        positions.saveAndFlush(position);
    }

    private void saveTrade(String id, int accountId, String security, String side, String state, int quantity) {
        Trade trade = new Trade();
        trade.setId(id);
        trade.setAccountId(accountId);
        trade.setSecurity(security);
        trade.setSide(side);
        trade.setState(state);
        trade.setQuantity(quantity);
        trades.saveAndFlush(trade);
    }
}
