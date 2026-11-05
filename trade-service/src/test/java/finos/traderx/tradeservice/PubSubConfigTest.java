package finos.traderx.tradeservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import finos.traderx.messaging.Publisher;
import finos.traderx.tradeservice.model.TradeOrder;
import finos.traderx.tradeservice.model.TradeSide;
import io.socket.client.Socket;

class PubSubConfigTest {

    @Test
    void tradePublisherTargetsConfiguredFeedAndPublishesToTradesTopicByDefault() throws Exception {
        PubSubConfig config = new PubSubConfig();
        ReflectionTestUtils.setField(config, "tradeFeedAddress", "http://feed.test:18086");

        Publisher<TradeOrder> publisher = config.tradePublisher();

        assertThat(ReflectionTestUtils.getField(publisher, "socketAddress")).isEqualTo("http://feed.test:18086");

        Socket socket = mock(Socket.class);
        ReflectionTestUtils.setField(publisher, "socket", socket);
        ReflectionTestUtils.setField(publisher, "connected", true);
        publisher.publish(new TradeOrder("order-1", 1, "IBM", TradeSide.Buy, 10));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(socket).emit(eq("publish"), captor.capture());
        assertThat(((JSONObject) captor.getValue()).getString("topic")).isEqualTo("/trades");
    }
}
