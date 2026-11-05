package finos.traderx.tradeprocessor.repository;

import java.util.List;
import java.time.LocalDate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import finos.traderx.tradeprocessor.model.TradeState;

import org.springframework.data.jpa.repository.JpaRepository;

import finos.traderx.tradeprocessor.model.Trade;

public interface TradeRepository extends JpaRepository<Trade, String> {
    
    List<Trade> findByAccountId(Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Trade> findByStateAndSettlementDateLessThanEqual(TradeState state, LocalDate date);
    
}
