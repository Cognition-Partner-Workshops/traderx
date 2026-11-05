package finos.traderx.tradeservice.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Pattern;

public class TradeOrder {

    public static final int MAX_QUANTITY = 1_000_000;
    private static final Pattern TICKER = Pattern.compile("[A-Z0-9][A-Z0-9.\\-]{0,9}");

    public String id;
    private String state;
    private String security;
    private Integer quantity;
    private Integer accountId;
    private TradeSide side;

    public TradeOrder(){}
    
    public TradeOrder(String id, int accountId, String security, TradeSide side, int quantity) {
        this.accountId = accountId;
        this.security = security;
        this.side = side;
        this.quantity = quantity;
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getState() {
        return state;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public String getSecurity() {
        return security;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public TradeSide getSide() {
        return side;
    }

    public boolean hasValidInput() {
        return quantity != null && quantity > 0 && quantity <= MAX_QUANTITY
                && accountId != null && accountId > 0
                && side != null
                && security != null && TICKER.matcher(security).matches();
    }

    @JsonSetter("quantity")
    private void readQuantity(JsonNode value) {
        quantity = readInteger(value);
    }

    @JsonSetter("accountId")
    private void readAccountId(JsonNode value) {
        accountId = readInteger(value);
    }

    @JsonSetter("side")
    private void readSide(JsonNode value) {
        if (value.isNull()) {
            side = null;
        } else if (value.isTextual()) {
            side = TradeSide.valueOf(value.textValue());
        } else {
            throw new IllegalArgumentException("side must be Buy or Sell");
        }
    }

    private static Integer readInteger(JsonNode value) {
        if (value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Expected a 32-bit integer");
        }
        return value.intValue();
    }
}
