package com.example.leavemanagement.exception;

// The request is valid but can't be applied to the current state (e.g. approving twice).
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
