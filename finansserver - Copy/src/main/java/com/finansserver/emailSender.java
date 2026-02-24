package com.finansserver;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class emailSender {

    //Console Color
    public static final String RESET = "\033[0m"; 
    public static final String GREEN = "\033[32m"; 
    public static final String YELLOW = "\033[33m"; 
    public static final String RED = "\033[31m";


    //Mail Variables
    private static Session session;
    private static String fromEmail;
    private static String subject;
    private static String messageBody;

    //Mail Connection
    static {
        // SMTP Mail server informations
        String host = "mail.kurumsaleposta.com";  
        String port = "587"; 
        String username = "info@yourgame.tech"; 
        String password = "EdizDeniz_209_";

        // Sender Info
        fromEmail = "info@yourgame.tech";
        subject = "Verification";

        // SMTP Settings
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true"); 

        // Verification
        session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    
    public static boolean sendMail(String toEmail, String url, String explainMessage) {
        messageBody = explainMessage + "\n \n http://localhost:8080/action/" + url;

        try {
            // Mail Nesnesini Oluşturma
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setText(messageBody);

            // Maili Gönder
            Transport.send(message);

            System.out.println(GREEN + "[INFO] VERIFICATION MAIL SENT TO " + toEmail);
            return true;
        } catch (MessagingException e) {

            System.out.println(RED + "[WARNING] MAIL COULDNT BE SENT TO " + toEmail);
            return false;
        }
    }


}