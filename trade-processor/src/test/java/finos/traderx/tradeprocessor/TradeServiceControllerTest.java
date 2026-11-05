package finos.traderx.tradeprocessor;

import finos.traderx.tradeprocessor.controller.TradeServiceController;
import finos.traderx.tradeprocessor.model.*;
import finos.traderx.tradeprocessor.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradeServiceController.class)
class TradeServiceControllerTest {
    @Autowired MockMvc mvc;
    @MockBean TradeService service;

    @Test void returnsBookingResultAndPassesRequestToService() throws Exception {
        when(service.processTrade(any())).thenAnswer(inv -> {
            TradeOrder order = inv.getArgument(0);
            org.assertj.core.api.Assertions.assertThat(order.getAccountId()).isEqualTo(7);
            org.assertj.core.api.Assertions.assertThat(order.getSecurity()).isEqualTo("IBM");
            org.assertj.core.api.Assertions.assertThat(order.getQuantity()).isEqualTo(4);
            org.assertj.core.api.Assertions.assertThat(order.getSide()).isEqualTo(TradeSide.Sell);
            Trade trade = new Trade();
            trade.setId("booked");
            trade.setState(TradeState.Settled);
            Position position = new Position();
            position.setQuantity(-4);
            return new TradeBookingResult(trade, position);
        });
        mvc.perform(post("/tradeservice/order").contentType("application/json")
            .content("{\"id\":\"order\",\"accountId\":7,\"security\":\"IBM\",\"quantity\":4,\"side\":\"Sell\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.trade.id").value("booked"))
            .andExpect(jsonPath("$.trade.state").value("Settled"))
            .andExpect(jsonPath("$.position.quantity").value(-4));
        verify(service).processTrade(any());
    }

    @Test void rejectsMissingBody() throws Exception {
        mvc.perform(post("/tradeservice/order").contentType("application/json"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void rejectsUnknownSide() throws Exception {
        mvc.perform(post("/tradeservice/order").contentType("application/json").content("{\"side\":\"INVALID\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
