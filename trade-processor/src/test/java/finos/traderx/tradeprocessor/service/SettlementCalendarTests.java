package finos.traderx.tradeprocessor.service;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementCalendarTests {
    @ParameterizedTest
    @CsvSource({
        "2026-11-02,2026-11-03", "2026-11-03,2026-11-04",
        "2026-11-04,2026-11-05", "2026-11-05,2026-11-06",
        "2026-11-06,2026-11-09", "2026-11-07,2026-11-09",
        "2026-11-08,2026-11-09", "2027-12-31,2028-01-03",
        "2024-02-28,2024-02-29", "2026-07-31,2026-08-03"
    })
    void calculatesNextBusinessDay(String booked, String expected) {
        assertEquals(LocalDate.parse(expected), SettlementCalendar.settlementDate(LocalDate.parse(booked)));
    }
}
