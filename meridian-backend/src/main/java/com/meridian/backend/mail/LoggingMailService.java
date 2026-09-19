package com.meridian.backend.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Used when no SMTP server is configured (local development): the email is
// written to the log so its link can be copied from there. The log then
// contains live reset links, so this must not be used in production.
public class LoggingMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailService.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("\n---- EMAIL (not sent: no SMTP server configured) ----\nTo: {}\nSubject: {}\n\n{}\n-----------------------------------------------------",
                to, subject, body);
    }
}
