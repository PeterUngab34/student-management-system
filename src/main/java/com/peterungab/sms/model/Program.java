package com.peterungab.sms.model;

/**
 * A degree program offered by the university, e.g. {@code BSCpE - BS Computer Engineering}.
 */
public record Program(int id, String code, String name, String department) {

    @Override
    public String toString() {
        return name;
    }
}
