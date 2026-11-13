package com.jobportal.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    @Autowired(required = false)
    private MailSender mailSender;

    @Value("${app.email.from:noreply@jobportal.com}")
    private String fromEmail;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    public void sendApplicationSubmittedEmail(String candidateEmail, String candidateName, 
                                            String jobTitle, String companyName) {
        if (!emailEnabled || mailSender == null) {
            logger.info("Email is disabled or mailSender not configured. Would send application submitted email to: {}", candidateEmail);
            return;
        }

        logger.info("Attempting to send application submitted email to: {}", candidateEmail);
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(candidateEmail);
            message.setSubject("Application Submitted Successfully - " + jobTitle);
            
            String emailBody = String.format(
                """
                Dear %s,

                Thank you for applying for the position of '%s' at %s.

                Your application has been successfully submitted and is now under review. \
                We will keep you updated on the status of your application.

                Application Details:
                - Position: %s
                - Company: %s
                - Status: Submitted

                Best regards,
                Job Portal Team
                """,
                candidateName, jobTitle, companyName, jobTitle, companyName
            );
            
            message.setText(emailBody);
            mailSender.send(message);
            
            logger.info("Application submitted email sent successfully to: {}", candidateEmail);
            
        } catch (org.springframework.mail.MailAuthenticationException e) {
            logger.error("SMTP Authentication failed for email to {}: {}", candidateEmail, e.getMessage());
        } catch (org.springframework.mail.MailSendException e) {
            logger.error("Failed to send email to {} due to SMTP error: {}", candidateEmail, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error sending application submitted email to: {}", candidateEmail, e);
        }
    }

    public void sendApplicationReceivedEmail(String companyEmail, String companyName, 
                                           String candidateName, String jobTitle) {
        if (!emailEnabled || mailSender == null) {
            logger.info("Email is disabled or mailSender not configured. Would send application received email to: {}", companyEmail);
            return;
        }

        logger.info("Attempting to send application received email to: {}", companyEmail);
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(companyEmail);
            message.setSubject("New Application Received - " + jobTitle);
            
            String emailBody = String.format(
                """
                Dear %s,

                You have received a new application for the position of '%s'.

                Applicant Details:
                - Name: %s
                - Position Applied: %s
                - Status: Submitted

                Please log into your Job Portal account to review the application.

                Best regards,
                Job Portal Team
                """,
                companyName, jobTitle, candidateName, jobTitle
            );
            
            message.setText(emailBody);
            mailSender.send(message);
            
            logger.info("Application received email sent successfully to: {}", companyEmail);
            
        } catch (org.springframework.mail.MailAuthenticationException e) {
            logger.error("SMTP Authentication failed for email to {}: {}", companyEmail, e.getMessage());
        } catch (org.springframework.mail.MailSendException e) {
            logger.error("Failed to send email to {} due to SMTP error: {}", companyEmail, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error sending application received email to: {}", companyEmail, e);
        }
    }

    public void sendApplicationStatusChangeEmail(String candidateEmail, String candidateName,
                                               String jobTitle, String companyName, 
                                               String oldStatus, String newStatus) {
        if (!emailEnabled || mailSender == null) {
            logger.info("Email is disabled or mailSender not configured. Would send status change email to: {}", candidateEmail);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(candidateEmail);
            message.setSubject("Application Status Update - " + jobTitle);
            
            String statusMessage = getStatusMessage(newStatus);
            
            String emailBody = String.format(
                """
                Dear %s,

                Your application status has been updated.

                Application Details:
                - Position: %s
                - Company: %s
                - Previous Status: %s
                - New Status: %s

                %s

                Best regards,
                Job Portal Team
                """,
                candidateName, jobTitle, companyName, oldStatus, newStatus, statusMessage
            );
            
            message.setText(emailBody);
            mailSender.send(message);
            
            logger.info("Application status change email sent successfully to: {}", candidateEmail);
            
        } catch (org.springframework.mail.MailAuthenticationException e) {
            logger.error("SMTP Authentication failed for status change email to {}: {}", candidateEmail, e.getMessage());
        } catch (org.springframework.mail.MailSendException e) {
            logger.error("Failed to send status change email to {} due to SMTP error: {}", candidateEmail, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error sending status change email to: {}", candidateEmail, e);
        }
    }

    private String getStatusMessage(String status) {
        return switch (status.toUpperCase()) {
            case "REVIEWED" -> "Your application has been reviewed by our team.";
            case "SHORTLISTED" -> "Congratulations! You have been shortlisted for the next round.";
            case "REJECTED" -> "Thank you for your interest. Unfortunately, your application was not selected this time.";
            case "HIRED" -> "Congratulations! We are pleased to offer you the position.";
            default -> "Your application status has been updated.";
        };
    }
}