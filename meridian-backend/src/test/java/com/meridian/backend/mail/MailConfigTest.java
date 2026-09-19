package com.meridian.backend.mail;

import com.meridian.backend.config.MailConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MailConfigTest {

    private final MailConfig config = new MailConfig();

    @Test
    void withoutASmtpHostEmailsAreOnlyLogged() {
        MailService service = config.mailService("", 587, "", "", true, "Meridian <noreply@example.com>");

        assertThat(service).isInstanceOf(LoggingMailService.class);
        service.send("a@b.io", "Hello", "Body"); // must not throw
    }

    @Test
    void withASmtpHostEmailsGoThroughSmtp() {
        MailService service = config.mailService("smtp.example.com", 2525, "user", "pw", true, "Meridian <noreply@example.com>");

        assertThat(service).isInstanceOf(SmtpMailService.class);
    }

    @Test
    void anSmtpMessageCarriesTheSenderRecipientSubjectAndBody() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new SmtpMailService(sender, "Meridian <noreply@example.com>").send("a@b.io", "Hi there", "The body");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getFrom()).isEqualTo("Meridian <noreply@example.com>");
        assertThat(sent.getValue().getTo()).containsExactly("a@b.io");
        assertThat(sent.getValue().getSubject()).isEqualTo("Hi there");
        assertThat(sent.getValue().getText()).isEqualTo("The body");
    }

    @Test
    void theTestProfileSendsSynchronouslyAndProductionInTheBackground() {
        assertThat(config.mailExecutor(false)).isInstanceOf(SyncTaskExecutor.class);
        assertThat(config.mailExecutor(true)).isNotInstanceOf(SyncTaskExecutor.class);
    }
}
