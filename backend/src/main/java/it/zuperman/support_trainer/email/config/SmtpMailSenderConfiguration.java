package it.zuperman.support_trainer.email.config;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.email", name = "mode", havingValue = "SMTP")
public class SmtpMailSenderConfiguration {

    @Bean
    public JavaMailSenderImpl smtpJavaMailSender(EmailProperties emailProperties) {
        EmailProperties.Smtp smtp = emailProperties.smtp();

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(smtp.host());
        mailSender.setPort(smtp.port());
        mailSender.setUsername(smtp.username());
        mailSender.setPassword(smtp.password());
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", Boolean.toString(smtp.auth()));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(smtp.startTls()));
        properties.put("mail.smtp.connectiontimeout", Long.toString(smtp.connectTimeout().toMillis()));
        properties.put("mail.smtp.timeout", Long.toString(smtp.readTimeout().toMillis()));
        properties.put("mail.smtp.writetimeout", Long.toString(smtp.writeTimeout().toMillis()));

        return mailSender;
    }
}
