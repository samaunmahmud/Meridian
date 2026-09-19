package com.meridian.backend.config;

import com.meridian.backend.mail.LoggingMailService;
import com.meridian.backend.mail.MailService;
import com.meridian.backend.mail.SmtpMailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Properties;

// Real SMTP when MAIL_HOST is set, otherwise emails are only logged.
// Sending happens on a small background pool so a slow mail server never slows
// (or, by timing, gives away) the response to "forgot password".
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    public MailService mailService(@Value("${mail.host:}") String host,
                                   @Value("${mail.port:587}") int port,
                                   @Value("${mail.username:}") String username,
                                   @Value("${mail.password:}") String password,
                                   @Value("${mail.starttls:true}") boolean startTls,
                                   @Value("${mail.from:Meridian <noreply@localhost>}") String from) {
        if (host.isBlank()) {
            log.warn("MAIL_HOST is not set: emails (password reset, verification) are only written to the log. "
                    + "Set MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD/MAIL_FROM to send them.");
            return new LoggingMailService();
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (!username.isBlank()) {
            sender.setUsername(username);
            sender.setPassword(password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(!username.isBlank()));
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        log.info("Sending email through {}:{} as {}", host, port, from);
        return new SmtpMailService(sender, from);
    }

    @Bean(name = "mailExecutor")
    public TaskExecutor mailExecutor(@Value("${mail.async:true}") boolean async) {
        if (!async) {
            return new SyncTaskExecutor(); // tests: send immediately so they can inspect the result
        }
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(100);
        pool.setThreadNamePrefix("mail-");
        pool.initialize();
        return pool;
    }
}
