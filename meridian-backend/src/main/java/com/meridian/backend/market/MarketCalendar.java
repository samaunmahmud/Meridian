package com.meridian.backend.market;

import com.meridian.backend.model.AssetType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When markets trade. Crypto is always open. Stocks trade in the NYSE/Nasdaq regular session,
 * 09:30-16:00 New York time on weekdays (13:00 on the few early-close days), and are closed on
 * exchange holidays. Holidays are worked out by rule for any year, so nothing needs updating each
 * January. Pre-market and after-hours sessions are not modelled: outside the regular session a
 * stock is "closed" and market orders wait for the open.
 */
@Component
public class MarketCalendar {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime OPEN = LocalTime.of(9, 30);
    private static final LocalTime CLOSE = LocalTime.of(16, 0);
    private static final LocalTime EARLY_CLOSE = LocalTime.of(13, 0);

    private final Clock clock;
    private final boolean enforced;
    private final Map<Integer, Set<LocalDate>> holidaysByYear = new ConcurrentHashMap<>();

    public MarketCalendar(Clock clock, @Value("${marketdata.hours-enforced:true}") boolean enforced) {
        this.clock = clock;
        this.enforced = enforced;
    }

    public boolean isOpen(AssetType type) {
        return status(type).open();
    }

    public MarketStatus status(AssetType type) {
        return status(type, clock.instant());
    }

    public MarketStatus status(AssetType type, Instant now) {
        if (type == AssetType.CRYPTO || !enforced) {
            return MarketStatus.alwaysOpen();
        }
        ZonedDateTime local = now.atZone(NEW_YORK);
        LocalDate day = local.toLocalDate();
        LocalTime time = local.toLocalTime();

        if (isTradingDay(day)) {
            if (!time.isBefore(OPEN) && time.isBefore(closeTime(day))) {
                return new MarketStatus(true, null, day.atTime(closeTime(day)).atZone(NEW_YORK).toInstant());
            }
            if (time.isBefore(OPEN)) {
                return new MarketStatus(false, day.atTime(OPEN).atZone(NEW_YORK).toInstant(), null);
            }
        }
        LocalDate next = day.plusDays(1);
        while (!isTradingDay(next)) next = next.plusDays(1);
        return new MarketStatus(false, next.atTime(OPEN).atZone(NEW_YORK).toInstant(), null);
    }

    /**
     * When the stock session last closed (at or before {@code now}), or null when trading hours are not
     * enforced. A price recorded after this moment is the closing price: nothing will move until the next open.
     */
    public Instant lastStockClose(Instant now) {
        if (!enforced) return null;
        ZonedDateTime local = now.atZone(NEW_YORK);
        LocalDate day = local.toLocalDate();
        if (!(isTradingDay(day) && !local.toLocalTime().isBefore(closeTime(day)))) {
            day = day.minusDays(1);
            while (!isTradingDay(day)) day = day.minusDays(1);
        }
        return day.atTime(closeTime(day)).atZone(NEW_YORK).toInstant();
    }

    boolean isTradingDay(LocalDate day) {
        DayOfWeek dow = day.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays(day.getYear()).contains(day);
    }

    LocalTime closeTime(LocalDate day) {
        // Day after Thanksgiving.
        if (day.equals(thanksgiving(day.getYear()).plusDays(1))) return EARLY_CLOSE;
        // Christmas Eve on a weekday that is itself a trading day.
        if (day.getMonth() == Month.DECEMBER && day.getDayOfMonth() == 24) return EARLY_CLOSE;
        // July 3rd, when July 4th is also a weekday (otherwise the holiday is observed on the 3rd).
        if (day.getMonth() == Month.JULY && day.getDayOfMonth() == 3) {
            DayOfWeek july4 = LocalDate.of(day.getYear(), Month.JULY, 4).getDayOfWeek();
            if (july4 != DayOfWeek.SATURDAY && july4 != DayOfWeek.SUNDAY) return EARLY_CLOSE;
        }
        return CLOSE;
    }

    private Set<LocalDate> holidays(int year) {
        return holidaysByYear.computeIfAbsent(year, MarketCalendar::computeHolidays);
    }

    private static Set<LocalDate> computeHolidays(int year) {
        Set<LocalDate> days = new HashSet<>();
        // A holiday on a Saturday is observed the Friday before, on a Sunday the Monday after.
        // The exception: when New Year's Day is a Saturday the exchange stays open on Dec 31.
        LocalDate newYear = LocalDate.of(year, Month.JANUARY, 1);
        if (newYear.getDayOfWeek() != DayOfWeek.SATURDAY) days.add(observed(newYear));
        days.add(nthWeekday(year, Month.JANUARY, DayOfWeek.MONDAY, 3));    // Martin Luther King Jr. Day
        days.add(nthWeekday(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3));   // Presidents' Day
        days.add(easterSunday(year).minusDays(2));                         // Good Friday
        days.add(LocalDate.of(year, Month.MAY, 31).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY))); // Memorial Day
        if (year >= 2022) days.add(observed(LocalDate.of(year, Month.JUNE, 19))); // Juneteenth
        days.add(observed(LocalDate.of(year, Month.JULY, 4)));             // Independence Day
        days.add(nthWeekday(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1));  // Labor Day
        days.add(thanksgiving(year));
        days.add(observed(LocalDate.of(year, Month.DECEMBER, 25)));        // Christmas
        return days;
    }

    private static LocalDate observed(LocalDate holiday) {
        return switch (holiday.getDayOfWeek()) {
            case SATURDAY -> holiday.minusDays(1);
            case SUNDAY -> holiday.plusDays(1);
            default -> holiday;
        };
    }

    private static LocalDate nthWeekday(int year, Month month, DayOfWeek dow, int n) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, dow));
    }

    private static LocalDate thanksgiving(int year) {
        return nthWeekday(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4);
    }

    // Anonymous Gregorian algorithm.
    private static LocalDate easterSunday(int year) {
        int a = year % 19, b = year / 100, c = year % 100;
        int d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
