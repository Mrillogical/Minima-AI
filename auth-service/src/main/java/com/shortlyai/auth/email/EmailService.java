package com.minima-AI.auth.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;

    private final String fromEmail;

    private final String baseUrl;

    private final String resetPwdUrl;

    public EmailService(
            JavaMailSender javaMailSender,

            @Value("${spring.mail.username}")
            String fromEmail,

            @Value("${app.base-url}")
            String baseUrl,

            @Value("${app.reset-pwd-url}")
            String resetPwdUrl
    ) {
        this.javaMailSender = javaMailSender;
        this.fromEmail = fromEmail;
        this.baseUrl = baseUrl;
        this.resetPwdUrl = resetPwdUrl;
    }

    @Async("emailExecutor")
    public void sendVerificationEmail(String toEmail, String token, String userName) {

        SimpleMailMessage mailMessage = new SimpleMailMessage();

        mailMessage.setSubject("Verify your minima-AI account");

        mailMessage.setText("Hi, "
                + userName
                + "\nPlease click this link to verify your minima-AI account\n"
                + baseUrl + token);

        mailMessage.setFrom(fromEmail);

        mailMessage.setTo(toEmail);

        javaMailSender.send(mailMessage);

        log.info("Account verification mail sent to user: {}", userName);
    }

    @Async("emailExecutor")
    public void sendResetPasswordEmail(String toEmail, String token, String userName) {

        SimpleMailMessage mailMessage = new SimpleMailMessage();

        mailMessage.setSubject("Reset your minima-AI password");

        mailMessage.setText(
                "Hi, "
                + userName
                + "\nPlease click this link to reset your minima-AI password\n"
                + resetPwdUrl + token
        );

        mailMessage.setFrom(fromEmail);

        mailMessage.setTo(toEmail);

        javaMailSender.send(mailMessage);

        log.info("Password reset mail sent to user: {}", userName);
    }
}


