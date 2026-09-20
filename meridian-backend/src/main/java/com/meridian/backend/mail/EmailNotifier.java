package com.meridian.backend.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

// Emails about things that happen while someone is not looking at the page (a price alert fired, a queued
// order filled). Sent in the background so a slow mail server never holds up price polling, and a failure
// only ever costs the email, never the event itself. Callers pass an address only for a CONFIRMED email
// (a typo'd address must not receive someone else's trading activity).
@Component
public class EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private final MailService mailService;
    private final TaskExecutor mailExecutor;

    public EmailNotifier(MailService mailService, @Qualifier("mailExecutor") TaskExecutor mailExecutor) {
        this.mailService = mailService;
        this.mailExecutor = mailExecutor;
    }

    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) return;
        try {
            mailExecutor.execute(() -> {
                try {
                    mailService.send(to, subject, body);
                } catch (Exception e) {
                    log.warn("Could not send \"{}\" email: {}", subject, e.toString());
                }
            });
        } catch (RuntimeException e) { // e.g. the mail queue is full
            log.warn("Could not queue \"{}\" email: {}", subject, e.toString());
        }
    }
}
