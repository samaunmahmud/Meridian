package com.meridian.backend.market;

import com.meridian.backend.model.AssetType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Dates below are checked against the published NYSE calendar. All times are New York time. */
class MarketCalendarTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private final MarketCalendar calendar = new MarketCalendar(Clock.systemUTC(), true);

    private static Instant at(String date, String time) {
        return ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), NY).toInstant();
    }

    private MarketStatus stocks(String date, String time) {
        return calendar.status(AssetType.STOCK, at(date, time));
    }

    @Test
    void isOpenDuringTheRegularSessionAndSaysWhenItCloses() {
        MarketStatus s = stocks("2026-09-16", "10:00"); // a Wednesday

        assertThat(s.open()).isTrue();
        assertThat(s.nextClose()).isEqualTo(at("2026-09-16", "16:00"));
        assertThat(s.nextOpen()).isNull();
    }

    @Test
    void opensAtHalfPastNineAndClosesAtFour() {
        assertThat(stocks("2026-09-16", "09:29").open()).isFalse();
        assertThat(stocks("2026-09-16", "09:30").open()).isTrue();
        assertThat(stocks("2026-09-16", "15:59").open()).isTrue();
        assertThat(stocks("2026-09-16", "16:00").open()).isFalse();
    }

    @Test
    void beforeTheOpenItOpensLaterTheSameDay() {
        assertThat(stocks("2026-09-16", "08:00").nextOpen()).isEqualTo(at("2026-09-16", "09:30"));
    }

    @Test
    void afterTheCloseItOpensTheNextTradingDay() {
        assertThat(stocks("2026-09-16", "17:00").nextOpen()).isEqualTo(at("2026-09-17", "09:30"));
    }

    @Test
    void aFridayEveningAndTheWeekendOpenOnMonday() {
        Instant monday = at("2026-09-21", "09:30");
        assertThat(stocks("2026-09-18", "17:00").nextOpen()).isEqualTo(monday);
        assertThat(stocks("2026-09-19", "12:00").open()).isFalse(); // Saturday
        assertThat(stocks("2026-09-19", "12:00").nextOpen()).isEqualTo(monday);
        assertThat(stocks("2026-09-20", "12:00").nextOpen()).isEqualTo(monday); // Sunday
    }

    @Test
    void followsDaylightSavingTime() {
        // 09:30 New York is 14:30 UTC in winter and 13:30 UTC in summer.
        assertThat(calendar.status(AssetType.STOCK, Instant.parse("2026-01-05T14:29:00Z")).open()).isFalse();
        assertThat(calendar.status(AssetType.STOCK, Instant.parse("2026-01-05T14:30:00Z")).open()).isTrue();
        assertThat(calendar.status(AssetType.STOCK, Instant.parse("2026-07-01T13:29:00Z")).open()).isFalse();
        assertThat(calendar.status(AssetType.STOCK, Instant.parse("2026-07-01T13:30:00Z")).open()).isTrue();
    }

    @Test
    void isClosedOnEveryExchangeHolidayOf2026() {
        List<String> holidays = List.of(
                "2026-01-01",  // New Year's Day
                "2026-01-19",  // Martin Luther King Jr. Day
                "2026-02-16",  // Presidents' Day
                "2026-04-03",  // Good Friday
                "2026-05-25",  // Memorial Day
                "2026-06-19",  // Juneteenth
                "2026-07-03",  // Independence Day (Saturday the 4th, observed on Friday)
                "2026-09-07",  // Labor Day
                "2026-11-26",  // Thanksgiving
                "2026-12-25"); // Christmas
        for (String day : holidays) {
            assertThat(stocks(day, "12:00").open()).as("open on holiday %s", day).isFalse();
        }
    }

    @Test
    void theNextOpenSkipsTheHoliday() {
        // Labor Day 2026 is Monday Sep 7: after the Friday close the next open is Tuesday.
        assertThat(stocks("2026-09-04", "17:00").nextOpen()).isEqualTo(at("2026-09-08", "09:30"));
        // Good Friday 2026: the Thursday before is the last session of that week.
        assertThat(stocks("2026-04-02", "17:00").nextOpen()).isEqualTo(at("2026-04-06", "09:30"));
    }

    @Test
    void closesEarlyAtOnePmOnTheFewEarlyCloseDays() {
        assertThat(stocks("2026-11-27", "12:59").open()).isTrue();   // day after Thanksgiving
        assertThat(stocks("2026-11-27", "13:00").open()).isFalse();
        assertThat(stocks("2026-12-24", "13:00").open()).isFalse();  // Christmas Eve (a Thursday)
        assertThat(stocks("2025-07-03", "13:00").open()).isFalse();  // July 4th 2025 is a Friday
        assertThat(stocks("2025-07-03", "12:59").open()).isTrue();
    }

    @Test
    void anOrdinaryDayStillClosesAtFour() {
        assertThat(stocks("2026-11-25", "15:59").open()).isTrue();   // the Wednesday before Thanksgiving
    }

    @Test
    void aHolidayOnASaturdayIsObservedOnFridayExceptNewYearsDay() {
        // Christmas 2027 is a Saturday: Friday Dec 24 is closed.
        assertThat(stocks("2027-12-24", "12:00").open()).isFalse();
        // New Year's Day 2028 is a Saturday: the exchange is OPEN on Friday Dec 31 2027.
        assertThat(stocks("2027-12-31", "12:00").open()).isTrue();
    }

    @Test
    void aHolidayOnASundayIsObservedOnMonday() {
        // Christmas 2022 was a Sunday: the exchange was closed on Monday Dec 26.
        assertThat(stocks("2022-12-26", "12:00").open()).isFalse();
    }

    @Test
    void juneteenthIsOnlyAHolidayFrom2022() {
        assertThat(stocks("2021-06-18", "12:00").open()).isTrue();  // an ordinary Friday in 2021
        assertThat(stocks("2023-06-19", "12:00").open()).isFalse();
    }

    @Test
    void cryptoTradesAroundTheClock() {
        assertThat(calendar.status(AssetType.CRYPTO, at("2026-12-25", "03:00")).open()).isTrue();
        assertThat(calendar.status(AssetType.CRYPTO, at("2026-09-19", "12:00")).nextOpen()).isNull();
    }

    @Test
    void canBeSwitchedOffSoStocksTradeAtAnyHour() {
        MarketCalendar off = new MarketCalendar(Clock.systemUTC(), false);
        assertThat(off.status(AssetType.STOCK, at("2026-09-19", "12:00")).open()).isTrue();
    }
}
