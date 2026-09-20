package com.meridian.backend;

import com.meridian.backend.dto.AlertRequest;
import com.meridian.backend.mail.MailService;
import com.meridian.backend.model.AlertDirection;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.AlertRepository;
import com.meridian.backend.service.AlertService;
import com.meridian.backend.websocket.PriceWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Alerts are checked on the price-polling thread, which (unlike a web request) has no database
 * session. These tests do not run inside a transaction, exactly like that thread.
 */
class AlertNotificationTest extends IntegrationTestBase {

    @Autowired AlertService alertService;
    @Autowired AlertRepository alertRepository;
    @MockBean PriceWebSocketHandler webSocket;
    @MockBean MailService mail;

    @Test
    void aTriggeredAlertReachesTheUserWhoSetItEvenFromTheSchedulerThread() {
        User user = newUser("0.00");
        Ticker ticker = newTicker("100.00");
        alertService.createAlert(new AlertRequest(ticker.getSymbol(), AlertDirection.ABOVE, new BigDecimal("110.00")), user);

        alertService.checkAlertsForTicker(ticker, new BigDecimal("111.00"));

        verify(webSocket).broadcastToUser(eq(user.getId()), contains("ALERT_TRIGGERED"));
        assertThat(alertRepository.findByTickerIdAndTriggeredFalse(ticker.getId())).as("alert is marked triggered").isEmpty();
    }

    @Test
    void anAlertThatHasNotCrossedItsTargetStaysQuiet() {
        User user = newUser("0.00");
        Ticker ticker = newTicker("100.00");
        alertService.createAlert(new AlertRequest(ticker.getSymbol(), AlertDirection.ABOVE, new BigDecimal("110.00")), user);

        alertService.checkAlertsForTicker(ticker, new BigDecimal("105.00"));

        verify(webSocket, never()).broadcastToUser(eq(user.getId()), contains("ALERT_TRIGGERED"));
    }

    private User userWithConfirmedEmail() {
        User user = newUser("0.00");
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    @Test
    void aUserWithAConfirmedAddressAlsoGetsAnEmail() {
        User user = userWithConfirmedEmail();
        Ticker ticker = newTicker("100.00");
        alertService.createAlert(new AlertRequest(ticker.getSymbol(), AlertDirection.BELOW, new BigDecimal("90.00")), user);

        alertService.checkAlertsForTicker(ticker, new BigDecimal("89.50"));

        verify(mail).send(eq(user.getEmail()), eq(ticker.getSymbol() + " fell below $90.00"),
                contains("is now $89.50"));
    }

    @Test
    void anUnconfirmedAddressGetsNoEmailButStillSeesTheAlertInTheApp() {
        User user = newUser("0.00"); // email not confirmed
        Ticker ticker = newTicker("100.00");
        alertService.createAlert(new AlertRequest(ticker.getSymbol(), AlertDirection.ABOVE, new BigDecimal("110.00")), user);

        alertService.checkAlertsForTicker(ticker, new BigDecimal("111.00"));

        verify(mail, never()).send(anyString(), anyString(), anyString());
        verify(webSocket).broadcastToUser(eq(user.getId()), contains("ALERT_TRIGGERED"));
    }
}
