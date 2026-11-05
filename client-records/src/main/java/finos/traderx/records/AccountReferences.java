package finos.traderx.records;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.jdbc.core.JdbcTemplate;

public final class AccountReferences {
    private static JdbcTemplate accounts;

    private AccountReferences() {}

    static void initialize(DataSource dataSource) {
        accounts = new JdbcTemplate(dataSource);
    }

    public static String reference(Integer id) {
        if (id == null) return null;
        return accounts.queryForObject("SELECT PublicReference FROM Accounts WHERE ID = ?", String.class, id);
    }

    public static Integer resolve(String reference) {
        if (reference.matches("[0-9]+")) return Integer.valueOf(reference);
        try {
            return accounts.queryForObject("SELECT ID FROM Accounts WHERE PublicReference = ?", Integer.class, reference);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Account not found");
        }
    }

    public static String masked(Integer id) {
        if (id == null) return null;
        String number = id.toString();
        return "****" + number.substring(Math.max(0, number.length() - 4));
    }

    public static class Serializer extends JsonSerializer<Integer> {
        @Override
        public void serialize(Integer value, JsonGenerator output, SerializerProvider provider) throws IOException {
            output.writeString(reference(value));
        }
    }

    public static class Deserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser input, DeserializationContext context) throws IOException {
            try {
                return resolve(input.getValueAsString());
            } catch (RuntimeException exception) {
                throw context.weirdStringException("", Integer.class, "Account not found");
            }
        }
    }

    public static class TradeIdSerializer extends JsonSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator output, SerializerProvider provider) throws IOException {
            output.writeString(value.matches("TRADE-[0-9]+-.*")
                    ? legacyTradeReference(value) : value);
        }
    }

    private static String legacyTradeReference(String value) {
        String secret = System.getProperty("traderx.reference.secret", System.getenv("TRADERX_REFERENCE_SECRET"));
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("TRADERX_REFERENCE_SECRET must contain at least 32 characters");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder reference = new StringBuilder();
            for (byte part : digest) reference.append(String.format("%02x", part & 0xff));
            return reference.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Legacy trade reference generation unavailable", exception);
        }
    }
}
