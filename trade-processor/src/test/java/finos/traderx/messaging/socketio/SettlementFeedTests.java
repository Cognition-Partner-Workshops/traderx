package finos.traderx.messaging.socketio;

import finos.traderx.tradeprocessor.model.Trade;
import finos.traderx.tradeprocessor.model.TradeState;
import io.socket.client.Socket;
import java.time.LocalDate;
import org.json.JSONObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementFeedTests {
    @ParameterizedTest
    @CsvSource({"2026-11-03,Processing", "2026-11-09,Processing", "2026-11-09,Settled"})
    void sendsIsoDateAndStateOnAccountTopic(String due, TradeState state) throws Exception {
        SocketIOJSONPublisher<Trade> publisher = new SocketIOJSONPublisher<>() {};
        publisher.socket = mock(Socket.class);
        publisher.connected = true;
        Trade trade = new Trade();
        trade.setId("trade-1");
        trade.setSettlementDate(LocalDate.parse(due));
        trade.setState(state);
        publisher.publish("/accounts/22214/trades", trade);
        ArgumentCaptor<JSONObject> json = ArgumentCaptor.forClass(JSONObject.class);
        verify(publisher.socket).emit(eq("publish"), json.capture());
        assertEquals("/accounts/22214/trades", json.getValue().getString("topic"));
        assertEquals(due, json.getValue().getJSONObject("payload").getString("settlementDate"));
        assertEquals(state.name(), json.getValue().getJSONObject("payload").getString("state"));
    }
}
