package com.meridian.backend.mail;

/** Sends one plain-text email. Failures throw; callers decide whether that matters. */
public interface MailService {
    void send(String to, String subject, String body);
}
