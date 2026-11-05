package finos.traderx.tradeprocessor.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import finos.traderx.tradeprocessor.model.TradeAudit;

public interface TradeAuditRepository extends JpaRepository<TradeAudit, String> {
}
