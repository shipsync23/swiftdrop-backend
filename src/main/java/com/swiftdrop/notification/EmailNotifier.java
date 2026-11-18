package com.swiftdrop.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends email over SMTP (MailHog in development: open http://localhost:8025). */
@Component
public class EmailNotifier implements Notifier {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotifier(JavaMailSender mailSender, @Value("${swiftdrop.mail-from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public void send(String recipient, String subject, String text) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(recipient);
        mail.setSubject(subject);
        mail.setText(text);
        mailSender.send(mail);
    }
}
