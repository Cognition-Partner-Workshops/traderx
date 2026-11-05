package finos.traderx.tradeprocessor;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SettlementConfig {
    @Bean
    public Clock settlementClock(@Value("${settlement.clock.zone:UTC}") String zone,
            @Value("${settlement.clock.offset:PT0S}") String offset) {
        return Clock.offset(Clock.system(ZoneId.of(zone)), Duration.parse(offset));
    }
}
