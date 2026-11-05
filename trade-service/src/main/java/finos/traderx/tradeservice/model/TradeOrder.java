package finos.traderx.tradeservice.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import finos.traderx.records.AccountReferences;

public class TradeOrder {

    public String id;
    private String state;
    private String security;
    private Integer quantity;
    private Integer accountId;
    private String accountReference;
    private TradeSide side;

    public TradeOrder(){}
    
    public TradeOrder(String id, int accountId, String security, TradeSide side, int quantity) {
        this.accountId = accountId;
        this.security = security;
        this.side = side;
        this.quantity = quantity;
        this.id = id;
    }

    @JsonSerialize(using = AccountReferences.TradeIdSerializer.class)
    public String getId() {
        return id;
    }

    public String getState() {
        return state;
    }

    @JsonDeserialize(using = AccountReferences.Deserializer.class)
    public Integer getAccountId() {
        return accountId;
    }

    public String getSecurity() {
        return security;
    }

    public String getAccountReference() {
        return accountReference;
    }

    public void setAccountReference(String reference) {
        this.accountReference = reference;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public TradeSide getSide() {
        return side;
    }
}
