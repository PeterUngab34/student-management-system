package com.peterungab.sms.dao;

/** Unchecked wrapper for {@link java.sql.SQLException}s thrown by the DAO layer. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
