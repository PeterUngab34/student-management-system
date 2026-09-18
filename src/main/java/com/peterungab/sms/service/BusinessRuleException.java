package com.peterungab.sms.service;

/** Thrown when an operation is valid input-wise but violates a business rule. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
