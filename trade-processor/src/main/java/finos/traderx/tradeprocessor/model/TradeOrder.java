package finos.traderx.tradeprocessor.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public class TradeOrder {

    public String id;
    private String state;
    @NotNull
    @Pattern(regexp = "^(?!.*\\.\\.)[A-Za-z0-9][A-Za-z0-9.-]{0,11}$")
    private String security;
    @NotNull
    @Positive
    private Integer quantity;
    @NotNull
    @Positive
    private Integer accountId;
    @NotNull
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
}
