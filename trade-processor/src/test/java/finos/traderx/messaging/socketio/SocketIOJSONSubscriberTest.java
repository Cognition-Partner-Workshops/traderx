package finos.traderx.messaging.socketio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import finos.traderx.messaging.Envelope;
import io.socket.client.IO;

class SocketIOJSONSubscriberTest {

    static class TestSubscriber extends SocketIOJSONSubscriber<String> {
        TestSubscriber() {
            super(String.class);
        }

        @Override
        public void onMessage(Envelope<?> envelope, String message) {
        }
    }

    @Test
    void sendsAuthTokenInHandshakeWhenConfigured() {
        TestSubscriber subscriber = new TestSubscriber();
        subscriber.setAuthToken("internal-token");
        IO.Options options = subscriber.getIOOptions();
        assertEquals("internal-token", options.auth.get("token"));
    }

    @Test
    void sendsNoAuthWhenTokenBlank() {
        TestSubscriber subscriber = new TestSubscriber();
        subscriber.setAuthToken("");
        assertNull(subscriber.getIOOptions().auth);
    }
}
