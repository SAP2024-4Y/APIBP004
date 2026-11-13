package com.jobportal.exception;

public class SchedulingConflictException extends RuntimeException {

    private final String code;

    public SchedulingConflictException(String message) {
        super(message);
        this.code = "SLOT_CONFLICT";
    }

    public SchedulingConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
