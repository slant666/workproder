package com.example.workorder.auth;

public class CsrfException extends RuntimeException {

    public CsrfException() {
        super("CSRF token is missing or invalid");
    }
}
