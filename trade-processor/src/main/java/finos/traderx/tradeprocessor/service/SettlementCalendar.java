package finos.traderx.tradeprocessor.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class SettlementCalendar {
    private SettlementCalendar() {}

    public static LocalDate settlementDate(LocalDate tradeDate) {
        LocalDate date = tradeDate.plusDays(1);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
}
