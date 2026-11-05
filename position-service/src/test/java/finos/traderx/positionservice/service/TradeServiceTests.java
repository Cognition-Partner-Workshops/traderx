package finos.traderx.positionservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import finos.traderx.positionservice.model.Trade;
import finos.traderx.positionservice.repository.TradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeServiceTests {
    @Mock TradeRepository tradeRepository;
    @InjectMocks TradeService tradeService;

    @Test
    void returnsAllTrades() {
        Trade trade = new Trade();
        trade.setState("Settled");
        when(tradeRepository.findAll()).thenReturn(List.of(trade));
        assertEquals(List.of(trade), tradeService.getAllTrades());
    }

    @Test
    void returnsEmptyTrades() {
        when(tradeRepository.findAll()).thenReturn(List.of());
        assertTrue(tradeService.getAllTrades().isEmpty());
    }

    @Test
    void queriesTradesForAccount() {
        Trade trade = new Trade();
        when(tradeRepository.findByAccountId(22214)).thenReturn(List.of(trade));
        assertEquals(List.of(trade), tradeService.getTradesByAccountID(22214));
        verify(tradeRepository).findByAccountId(22214);
    }

    @Test
    void propagatesRepositoryFailure() {
        when(tradeRepository.findAll()).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, tradeService::getAllTrades);
    }
}
