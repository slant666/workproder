package com.example.workorder.email;

public interface EmailSender {

    void send(String to, String subject, String body);
}
