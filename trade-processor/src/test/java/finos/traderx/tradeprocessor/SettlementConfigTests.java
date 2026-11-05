package finos.traderx.tradeprocessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class SettlementConfigTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SettlementConfig.class);

    @Test
    void defaultsToUtcWithoutOffset() {
        Instant before = Instant.now();
        contextRunner.run(context -> {
            Clock clock = context.getBean(Clock.class);
            assertEquals(ZoneId.of("UTC"), clock.getZone());
            assertFalse(clock.instant().isBefore(before));
            assertFalse(clock.instant().isAfter(Instant.now()));
        });
    }

    @Test
    void offsetMakesTradeDueTomorrowEligibleInConfiguredZone() {
        contextRunner.withPropertyValues("settlement.clock.zone=America/New_York",
            "settlement.clock.offset=PT24H").run(context -> {
                Clock clock = context.getBean(Clock.class);
                assertEquals(ZoneId.of("America/New_York"), clock.getZone());
                Clock unshifted = Clock.offset(clock, Duration.ofHours(-24));
                LocalDate settlementDate = LocalDate.now(unshifted).plusDays(1);
                assertTrue(settlementDate.isAfter(LocalDate.now(unshifted)));
                assertFalse(settlementDate.isAfter(LocalDate.now(clock)));
            });
    }
}
