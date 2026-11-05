package finos.traderx.positionservice;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import finos.traderx.positionservice.controller.PositionController;
import finos.traderx.positionservice.controller.TradeController;
import finos.traderx.positionservice.service.PositionService;
import finos.traderx.positionservice.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({PositionController.class, TradeController.class})
class BlotterErrorTests {

    @Autowired MockMvc mvc;
    @MockBean PositionService positionService;
    @MockBean TradeService tradeService;

    @Test
    void positionQueryFailureReturnsServerErrorWithMessage() throws Exception {
        when(positionService.getPositionsByAccountID(anyInt())).thenThrow(new IllegalStateException("positions unavailable"));

        mvc.perform(get("/positions/101"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("positions unavailable"));
    }

    @Test
    void tradeQueryFailureReturnsServerErrorWithMessage() throws Exception {
        when(tradeService.getTradesByAccountID(anyInt())).thenThrow(new IllegalStateException("trades unavailable"));

        mvc.perform(get("/trades/101"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("trades unavailable"));
    }
}
