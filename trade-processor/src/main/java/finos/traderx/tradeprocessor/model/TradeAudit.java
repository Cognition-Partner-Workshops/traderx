package finos.traderx.tradeprocessor.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "TRADEAUDIT")
public class TradeAudit {
    @Id
    @Column(name = "ID", length = 50)
    private String id;

    @Column(name = "TRADEID", length = 50, nullable = false)
    private String tradeId;

    @Column(name = "ACTOR", length = 100, nullable = false)
    private String actor;

    @Column(name = "ACTION", length = 50, nullable = false)
    private String action;

    @Column(name = "ACCOUNTID", nullable = false)
    private Integer accountId;

    @Column(name = "SECURITY", length = 15, nullable = false)
    private String security;

    @Enumerated(EnumType.STRING)
    @Column(name = "SIDE", length = 10, nullable = false)
    private TradeSide side;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    @Column(name = "CREATEDUTC", nullable = false)
    private OffsetDateTime createdUtc;

    protected TradeAudit() {}

    public TradeAudit(Trade trade, String actor) {
        this.id = UUID.randomUUID().toString();
        this.tradeId = trade.getId();
        this.actor = actor;
        this.action = "TRADE_BOOKED";
        this.accountId = trade.getAccountId();
        this.security = trade.getSecurity();
        this.side = trade.getSide();
        this.quantity = trade.getQuantity();
        this.createdUtc = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
