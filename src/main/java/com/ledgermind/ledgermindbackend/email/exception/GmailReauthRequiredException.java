package com.ledgermind.ledgermindbackend.email.exception;

public class GmailReauthRequiredException extends RuntimeException {
    public GmailReauthRequiredException(String message) {
        super(message);
    }
}
