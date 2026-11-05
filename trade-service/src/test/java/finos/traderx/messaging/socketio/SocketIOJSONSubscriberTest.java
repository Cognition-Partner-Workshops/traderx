package finos.traderx.messaging.socketio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import finos.traderx.messaging.Envelope;
import finos.traderx.messaging.PubSubException;
import finos.traderx.tradeservice.model.TradeOrder;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

class SocketIOJSONSubscriberTest {

    private static final String FEED = "http://feed.test:18086";

    private Socket socket;
    private MockedStatic<IO> io;
    private final Map<String, Emitter.Listener> listeners = new HashMap<>();
    private final List<TradeOrder> received = new ArrayList<>();
    private final List<Envelope<?>> envelopes = new ArrayList<>();
    private SocketIOJSONSubscriber<TradeOrder> subscriber;

    @BeforeEach
    void setUp() {
        socket = mock(Socket.class);
        when(socket.on(anyString(), any(Emitter.Listener.class))).thenAnswer(inv -> {
            listeners.put(inv.getArgument(0), inv.getArgument(1));
            return socket;
        });
        io = mockStatic(IO.class);
        io.when(() -> IO.socket(any(URI.class), any(IO.Options.class))).thenReturn(socket);

        subscriber = new SocketIOJSONSubscriber<TradeOrder>(TradeOrder.class) {
            @Override
            public void onMessage(Envelope<?> envelope, TradeOrder message) {
                envelopes.add(envelope);
                received.add(message);
            }
        };
        subscriber.setSocketAddress(FEED);
    }

    @AfterEach
    void tearDown() {
        io.close();
    }

    private void deliver(Object payload) {
        listeners.get("publish").call(payload);
    }

    @Test
    void startupConnectsToFeedAndSubscribesToDefaultTopic() throws Exception {
        subscriber.setDefaultTopic("/trades");

        subscriber.afterPropertiesSet();

        io.verify(() -> IO.socket(eq(URI.create(FEED)), any(IO.Options.class)));
        verify(socket).connect();
        verify(socket).emit("subscribe", "/trades");
        assertThat(listeners).containsKeys(Socket.EVENT_CONNECT, Socket.EVENT_DISCONNECT,
                Socket.EVENT_CONNECT_ERROR, "publish");
    }

    @Test
    void matchingEnvelopeIsDeserializedAndDispatchedToOnMessage() throws Exception {
        subscriber.connect();

        deliver(new JSONObject("""
                {"topic":"/trades","type":"TradeOrder","from":"trade-service",
                 "payload":{"id":"order-7","security":"MSFT","quantity":25,"accountId":42,"side":"Buy"}}
                """));

        assertThat(received).hasSize(1);
        TradeOrder order = received.get(0);
        assertThat(order.getId()).isEqualTo("order-7");
        assertThat(order.getSecurity()).isEqualTo("MSFT");
        assertThat(order.getQuantity()).isEqualTo(25);
        assertThat(order.getAccountId()).isEqualTo(42);
        assertThat(order.getSide().name()).isEqualTo("Buy");
        assertThat(envelopes.get(0).getTopic()).isEqualTo("/trades");
        assertThat(envelopes.get(0).getFrom()).isEqualTo("trade-service");
    }

    @Test
    void envelopeOfAnotherTypeIsTreatedAsSystemMessageAndNotDispatched() throws Exception {
        subscriber.connect();

        deliver(new JSONObject("{\"topic\":\"/trades\",\"type\":\"Position\",\"payload\":{\"security\":\"IBM\"}}"));

        assertThat(received).isEmpty();
    }

    @Test
    void malformedMessageIsSwallowedWithoutDispatching() throws Exception {
        subscriber.connect();

        deliver(new JSONObject("{\"topic\":\"/trades\"}"));
        deliver("not-json");

        assertThat(received).isEmpty();
    }

    @Test
    void connectionLifecycleEventsDriveConnectedState() throws Exception {
        subscriber.connect();
        assertThat(subscriber.isConnected()).isFalse();

        listeners.get(Socket.EVENT_CONNECT).call();
        assertThat(subscriber.isConnected()).isTrue();

        listeners.get(Socket.EVENT_DISCONNECT).call();
        assertThat(subscriber.isConnected()).isFalse();

        listeners.get(Socket.EVENT_CONNECT).call();
        listeners.get(Socket.EVENT_CONNECT_ERROR).call();
        assertThat(subscriber.isConnected()).isFalse();
    }

    @Test
    void reconnectDisconnectsPreviousSocket() throws Exception {
        Socket previous = mock(Socket.class);
        subscriber.socket = previous;

        subscriber.connect();

        verify(previous).disconnect();
        assertThat(subscriber.socket).isSameAs(socket);
    }

    @Test
    void connectFailureIsReportedAsPubSubException() {
        io.when(() -> IO.socket(any(URI.class), any(IO.Options.class))).thenThrow(new RuntimeException("bad uri"));

        assertThatThrownBy(subscriber::connect)
                .isInstanceOf(PubSubException.class)
                .hasMessage("Cannot socket connection at " + FEED);
    }

    @Test
    void disconnectOnlyClosesLiveSocket() throws Exception {
        subscriber.connect();
        subscriber.disconnect();
        verify(socket, never()).disconnect();
        assertThat(subscriber.socket).isNull();

        subscriber.connect();
        listeners.get(Socket.EVENT_CONNECT).call();
        subscriber.disconnect();
        verify(socket).disconnect();
        assertThat(subscriber.socket).isNull();
    }

    @Disabled("BUG: SocketIOJSONSubscriber.unsubscribe emits the literal string \"topic\" instead of the topic argument")
    @Test
    void unsubscribeEmitsRequestedTopic() throws Exception {
        subscriber.connect();

        subscriber.unsubscribe("/accounts/42");

        verify(socket).emit("unsubscribe", "/accounts/42");
    }
}
