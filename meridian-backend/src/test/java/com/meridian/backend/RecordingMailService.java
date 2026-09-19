package com.meridian.backend;

import com.meridian.backend.mail.MailService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stands in for the mail server in tests: remembers what would have been sent. */
public class RecordingMailService implements MailService {

    public record Mail(String to, String subject, String body) {
    }

    private final List<Mail> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    @Override
    public void send(String to, String subject, String body) {
        if (failing) {
            throw new IllegalStateException("mail server is down");
        }
        sent.add(new Mail(to, subject, body));
    }

    public void failFromNowOn(boolean failing) {
        this.failing = failing;
    }

    public List<Mail> sentTo(String address) {
        return sent.stream().filter(m -> m.to().equals(address)).toList();
    }

    public Mail lastTo(String address) {
        List<Mail> mails = sentTo(address);
        return mails.isEmpty() ? null : mails.get(mails.size() - 1);
    }

    /** The token in the link of the latest email to `address`, e.g. the value of ?reset=... */
    public String lastToken(String address, String parameter) {
        Mail mail = lastTo(address);
        if (mail == null) return null;
        Matcher m = Pattern.compile("[?&]" + parameter + "=([A-Za-z0-9_-]+)").matcher(mail.body());
        return m.find() ? m.group(1) : null;
    }
}
