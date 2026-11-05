package finos.traderx.tradeprocessor.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SettlementScheduler {
    private final TradeService tradeService;

    public SettlementScheduler(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @Scheduled(fixedDelayString = "${settlement.poll-interval-ms:1000}")
    public void settleDueTrades() {
        tradeService.settleDueTrades();
    }
}
