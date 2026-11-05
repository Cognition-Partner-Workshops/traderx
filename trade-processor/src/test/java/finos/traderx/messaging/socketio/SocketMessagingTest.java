package finos.traderx.messaging.socketio;

import finos.traderx.messaging.*;
import finos.traderx.tradeprocessor.model.*;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import java.net.URI;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SocketMessagingTest {
    static class Receiver extends SocketIOJSONSubscriber<TradeOrder> {
        TradeOrder received;
        Envelope<?> envelope;
        Receiver() { super(TradeOrder.class); }
        public void onMessage(Envelope<?> envelope, TradeOrder message) {
            this.envelope = envelope;
            received = message;
        }
    }

    @Test void publisherSerializesEnvelopeAndUsesConfiguredTopic() throws Exception {
        Socket socket = mock(Socket.class);
        SocketIOJSONPublisher<TradeOrder> publisher = new SocketIOJSONPublisher<>() {};
        publisher.socket = socket;
        publisher.connected = true;
        publisher.setTopic("/trades");
        publisher.publish(new TradeOrder("order", 7, "IBM", TradeSide.Buy, 3));
        ArgumentCaptor<JSONObject> json = ArgumentCaptor.forClass(JSONObject.class);
        verify(socket).emit(eq("publish"), json.capture());
        JSONObject envelope = json.getValue();
        assertThat(envelope.getString("topic")).isEqualTo("/trades");
        assertThat(envelope.getString("type")).isEqualTo("TradeOrder");
        assertThat(envelope.has("from")).isFalse();
        assertThat(envelope.getJSONObject("payload").getString("id")).isEqualTo("order");
        assertThat(envelope.getJSONObject("payload").getInt("quantity")).isEqualTo(3);
    }

    @Test void disconnectedPublisherRejectsSend() {
        SocketIOJSONPublisher<String> publisher = new SocketIOJSONPublisher<>() {};
        assertThatThrownBy(() -> publisher.publish("/trades", "message"))
            .isInstanceOf(PubSubException.class).hasMessageContaining("not connected").hasMessageContaining("/trades");
    }

    @Test void connectionFailuresAreWrapped() {
        SocketIOJSONPublisher<String> publisher = new SocketIOJSONPublisher<>() {
            protected Socket internalConnect(URI uri) { throw new IllegalStateException("offline"); }
        };
        Receiver receiver = new Receiver() {
            protected Socket internalConnect(URI uri) { throw new IllegalStateException("offline"); }
        };
        assertThatThrownBy(publisher::connect).isInstanceOf(PubSubException.class).hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(receiver::connect).isInstanceOf(PubSubException.class).hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test void subscriberSubscribesDeserializesAndIgnoresOtherTypesAndMalformedEvents() throws Exception {
        Socket socket = mock(Socket.class);
        try (MockedStatic<IO> io = mockStatic(IO.class)) {
            io.when(() -> IO.socket(any(URI.class), any(IO.Options.class))).thenReturn(socket);
            Receiver receiver = new Receiver();
            receiver.setDefaultTopic("/trades");
            receiver.afterPropertiesSet();
            verify(socket).emit("subscribe", "/trades");
            verify(socket).connect();
            ArgumentCaptor<Emitter.Listener> listener = ArgumentCaptor.forClass(Emitter.Listener.class);
            verify(socket).on(eq("publish"), listener.capture());
            listener.getValue().call(new JSONObject("{\"type\":\"Other\"}"));
            assertThat(receiver.received).isNull();
            listener.getValue().call(new JSONObject("{\"type\":\"TradeOrder\",\"topic\":\"/trades\",\"payload\":{\"id\":\"x\",\"accountId\":7,\"security\":\"IBM\",\"side\":\"Sell\",\"quantity\":5}}"));
            assertThat(receiver.received.getId()).isEqualTo("x");
            assertThat(receiver.received.getSide()).isEqualTo(TradeSide.Sell);
            assertThat(receiver.received.getQuantity()).isEqualTo(5);
            assertThat(receiver.envelope.getTopic()).isEqualTo("/trades");
            TradeOrder previous = receiver.received;
            listener.getValue().call("bad payload");
            assertThat(receiver.received).isSameAs(previous);
            ArgumentCaptor<Emitter.Listener> connected = ArgumentCaptor.forClass(Emitter.Listener.class);
            verify(socket).on(eq(Socket.EVENT_CONNECT), connected.capture());
            connected.getValue().call();
            assertThat(receiver.isConnected()).isTrue();
            receiver.disconnect();
            verify(socket).disconnect();
        }
    }

    @Test void publisherTracksConnectionEvents() throws Exception {
        Socket mockSocket = mock(Socket.class);
        SocketIOJSONPublisher<String> publisher = new SocketIOJSONPublisher<>() {
            protected Socket internalConnect(URI uri) { return mockSocket; }
        };
        publisher.afterPropertiesSet();
        ArgumentCaptor<Emitter.Listener> connected = ArgumentCaptor.forClass(Emitter.Listener.class);
        verify(mockSocket).on(eq(Socket.EVENT_CONNECT), connected.capture());
        connected.getValue().call();
        assertThat(publisher.isConnected()).isTrue();
        ArgumentCaptor<Emitter.Listener> disconnected = ArgumentCaptor.forClass(Emitter.Listener.class);
        verify(mockSocket).on(eq(Socket.EVENT_DISCONNECT), disconnected.capture());
        disconnected.getValue().call();
        assertThat(publisher.isConnected()).isFalse();
        ArgumentCaptor<Emitter.Listener> error = ArgumentCaptor.forClass(Emitter.Listener.class);
        verify(mockSocket).on(eq(Socket.EVENT_CONNECT_ERROR), error.capture());
        connected.getValue().call();
        error.getValue().call();
        assertThat(publisher.isConnected()).isFalse();
    }

    @Disabled("Bug: unsubscribe emits the literal 'topic' instead of the requested topic")
    @Test void unsubscribeUsesRequestedTopic() throws Exception {
        Receiver receiver = new Receiver();
        receiver.socket = mock(Socket.class);
        receiver.unsubscribe("/trades");
        verify(receiver.socket).emit("unsubscribe", "/trades");
    }

    @Disabled("Bug: publisher swallows serialization/socket errors instead of reporting PubSubException")
    @Test void socketSendFailureIsReported() {
        SocketIOJSONPublisher<String> publisher = new SocketIOJSONPublisher<>() {};
        publisher.connected = true;
        publisher.socket = mock(Socket.class);
        doThrow(new IllegalStateException("send failed")).when(publisher.socket).emit(eq("publish"), any(JSONObject.class));
        assertThatThrownBy(() -> publisher.publish("message")).isInstanceOf(PubSubException.class);
    }
}
