package com.meridian.backend;

import com.meridian.backend.dto.WatchlistItemResponse;
import com.meridian.backend.dto.WatchlistNoteRequest;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.service.WatchlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchlistNotesTest extends IntegrationTestBase {

    @Autowired WatchlistService watchlistService;

    private static WatchlistNoteRequest note(String text, String target) {
        return new WatchlistNoteRequest(text, target == null ? null : new BigDecimal(target));
    }

    @Test
    void savingANoteAddsTheStockToYourWatchlistWithItsCurrentPrice() {
        User user = newUser("1000.00");
        Ticker t = newTicker("231.40");

        WatchlistItemResponse saved = watchlistService.saveNote(t.getSymbol().toLowerCase(), note("  Wait for the earnings dip.  ", "220"), user);

        assertThat(saved.note()).isEqualTo("Wait for the earnings dip.");
        assertThat(saved.targetPrice()).isEqualByComparingTo("220");
        assertThat(saved.currentPrice()).isEqualByComparingTo("231.40");
        List<WatchlistItemResponse> list = watchlistService.getWatchlist(user);
        assertThat(list).singleElement().satisfies(i -> {
            assertThat(i.symbol()).isEqualTo(t.getSymbol());
            assertThat(i.note()).isEqualTo("Wait for the earnings dip.");
        });
    }

    @Test
    void savingAgainUpdatesTheSameEntryAndBlankClears() {
        User user = newUser("1000.00");
        Ticker t = newTicker("10.00");
        watchlistService.addToWatchlist(t.getSymbol(), user);

        watchlistService.saveNote(t.getSymbol(), note("First", "9"), user);
        WatchlistItemResponse cleared = watchlistService.saveNote(t.getSymbol(), note("   ", null), user);

        assertThat(cleared.note()).isNull();
        assertThat(cleared.targetPrice()).isNull();
        assertThat(watchlistService.getWatchlist(user)).hasSize(1);
    }

    @Test
    void notesArePrivateToEachAccount() {
        User me = newUser("1000.00");
        User you = newUser("1000.00");
        Ticker t = newTicker("10.00");
        watchlistService.saveNote(t.getSymbol(), note("Mine", null), me);

        assertThat(watchlistService.getWatchlist(you)).isEmpty();
    }

    @Test
    void badNotesAndTargetsAreRefused() {
        User user = newUser("1000.00");
        Ticker t = newTicker("10.00");

        assertThatThrownBy(() -> watchlistService.saveNote(t.getSymbol(), note("x".repeat(501), null), user))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> watchlistService.saveNote(t.getSymbol(), note(null, "0"), user))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> watchlistService.saveNote(t.getSymbol(), note(null, "-5"), user))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> watchlistService.saveNote(t.getSymbol(), note(null, "1.23456"), user))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> watchlistService.saveNote("NOPE999", note("hi", null), user))
                .isInstanceOf(TickerNotFoundException.class);
        assertThat(watchlistService.saveNote(t.getSymbol(), note("x".repeat(500), "0.0001"), user).note()).hasSize(500);
    }
}
