package finos.traderx.tradeprocessor;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SettlementConfig {
    @Bean
    public Clock settlementClock() {
        return Clock.systemUTC();
    }
}
