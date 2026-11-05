package finos.traderx.tradeservice.model;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Customer-facing account numbers show only their last four digits.
 */
public final class AccountNumberMask {

    private AccountNumberMask() {
    }

    public static String mask(Integer accountId) {
        if (accountId == null) {
            return null;
        }
        String digits = String.valueOf(accountId);
        return "****" + digits.substring(Math.max(0, digits.length() - 4));
    }

    public static class Serializer extends StdSerializer<Integer> {

        public Serializer() {
            super(Integer.class);
        }

        @Override
        public void serialize(Integer accountId, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(mask(accountId));
        }
    }
}
