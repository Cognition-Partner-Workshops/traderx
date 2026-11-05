package finos.traderx.messaging.socketio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import finos.traderx.messaging.PubSubException;
import finos.traderx.tradeservice.model.TradeOrder;
import finos.traderx.tradeservice.model.TradeSide;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

class SocketIOJSONPublisherTest {

    private Socket feedSocket;
    private SocketIOJSONPublisher<TradeOrder> publisher;

    @BeforeEach
    void setUp() {
        feedSocket = mock(Socket.class);
        publisher = new SocketIOJSONPublisher<TradeOrder>() {
            @Override
            protected Socket internalConnect(URI uri) {
                return feedSocket;
            }
        };
    }

    private TradeOrder order() {
        return new TradeOrder("order-1", 22214, "IBM", TradeSide.Sell, 50);
    }

    private JSONObject capturePublished() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(feedSocket).emit(eq("publish"), captor.capture());
        return (JSONObject) captor.getValue();
    }

    private Map<String, Emitter.Listener> startAndCaptureListeners() throws Exception {
        Map<String, Emitter.Listener> listeners = new HashMap<>();
        org.mockito.Mockito.when(feedSocket.on(anyString(), any(Emitter.Listener.class))).thenAnswer(inv -> {
            listeners.put(inv.getArgument(0), inv.getArgument(1));
            return feedSocket;
        });
        publisher.afterPropertiesSet();
        return listeners;
    }

    @Test
    void publishingWhileDisconnectedFailsWithoutEmitting() {
        publisher.socket = feedSocket;

        assertThatThrownBy(() -> publisher.publish("/trades", order()))
                .isInstanceOf(PubSubException.class)
                .hasMessageContaining("not connected")
                .hasMessageContaining("/trades");
        verify(feedSocket, never()).emit(anyString(), any(Object[].class));
    }

    @Test
    void publishWrapsPayloadInTypedEnvelopeOnRequestedTopic() throws Exception {
        publisher.socket = feedSocket;
        publisher.connected = true;

        publisher.publish("/accounts/22214/trades", order());

        JSONObject envelope = capturePublished();
        assertThat(envelope.getString("topic")).isEqualTo("/accounts/22214/trades");
        assertThat(envelope.getString("type")).isEqualTo("TradeOrder");
        assertThat(envelope.has("date")).isTrue();
        assertThat(envelope.has("from")).as("null fields are omitted").isFalse();
        JSONObject payload = envelope.getJSONObject("payload");
        assertThat(payload.getString("id")).isEqualTo("order-1");
        assertThat(payload.getString("security")).isEqualTo("IBM");
        assertThat(payload.getInt("accountId")).isEqualTo(22214);
        assertThat(payload.getInt("quantity")).isEqualTo(50);
        assertThat(payload.getString("side")).isEqualTo("Sell");
        assertThat(payload.has("state")).as("null state is omitted").isFalse();
    }

    @Test
    void publishWithoutTopicUsesConfiguredDefaultTopic() throws Exception {
        publisher.socket = feedSocket;
        publisher.connected = true;
        publisher.setTopic("/trades");

        publisher.publish(order());

        assertThat(capturePublished().getString("topic")).isEqualTo("/trades");
    }

    @Test
    void unserializablePayloadIsSwallowedAndNothingIsEmitted() throws Exception {
        SocketIOJSONPublisher<Object> raw = new SocketIOJSONPublisher<Object>() {};
        raw.socket = feedSocket;
        raw.connected = true;

        raw.publish("/trades", new Object() {
            @SuppressWarnings("unused")
            public String getBoom() {
                throw new IllegalStateException("cannot serialize");
            }
        });

        verify(feedSocket, never()).emit(anyString(), any(Object[].class));
    }

    @Test
    void connectReplacesExistingSocketAfterDisconnectingIt() throws Exception {
        Socket previous = mock(Socket.class);
        publisher.socket = previous;

        publisher.connect();

        verify(previous).disconnect();
        assertThat(publisher.socket).isSameAs(feedSocket);
    }

    @Test
    void connectFailureIsReportedAsPubSubExceptionNamingTheAddress() {
        SocketIOJSONPublisher<TradeOrder> failing = new SocketIOJSONPublisher<TradeOrder>() {
            @Override
            protected Socket internalConnect(URI uri) throws Exception {
                throw new IllegalStateException("refused");
            }
        };
        failing.setSocketAddress("http://feed.test:18086");

        assertThatThrownBy(failing::connect)
                .isInstanceOf(PubSubException.class)
                .hasMessage("Cannot socket connection at http://feed.test:18086")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void defaultInternalConnectOpensSocketAtConfiguredAddress() throws Exception {
        SocketIOJSONPublisher<TradeOrder> real = new SocketIOJSONPublisher<TradeOrder>() {};
        real.setSocketAddress("http://feed.test:18086");
        try (MockedStatic<IO> io = mockStatic(IO.class)) {
            io.when(() -> IO.socket(any(URI.class), any(IO.Options.class))).thenReturn(feedSocket);

            real.connect();

            io.verify(() -> IO.socket(eq(URI.create("http://feed.test:18086")), any(IO.Options.class)));
            assertThat(real.socket).isSameAs(feedSocket);
        }
    }

    @Test
    void connectionLifecycleEventsDriveConnectedState() throws Exception {
        Map<String, Emitter.Listener> listeners = startAndCaptureListeners();

        InOrder order = inOrder(feedSocket);
        order.verify(feedSocket).on(eq(Socket.EVENT_CONNECT), any(Emitter.Listener.class));
        order.verify(feedSocket).connect();
        assertThat(publisher.isConnected()).isFalse();

        listeners.get(Socket.EVENT_CONNECT).call();
        assertThat(publisher.isConnected()).isTrue();

        listeners.get(Socket.EVENT_DISCONNECT).call("transport close");
        assertThat(publisher.isConnected()).isFalse();

        listeners.get(Socket.EVENT_CONNECT).call();
        listeners.get(Socket.EVENT_CONNECT_ERROR).call(new RuntimeException("boom"));
        assertThat(publisher.isConnected()).isFalse();
    }

    @Test
    void disconnectClosesLiveSocketAndForgetsIt() throws Exception {
        publisher.socket = feedSocket;
        publisher.connected = true;

        publisher.disconnect();

        verify(feedSocket).disconnect();
        assertThat(publisher.socket).isNull();
    }

    @Test
    void disconnectDoesNotTouchSocketThatNeverConnected() throws Exception {
        publisher.socket = feedSocket;

        publisher.disconnect();

        verify(feedSocket, never()).disconnect();
        assertThat(publisher.socket).isNull();
    }
}
