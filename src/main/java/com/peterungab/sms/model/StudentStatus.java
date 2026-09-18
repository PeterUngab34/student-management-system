package com.peterungab.sms.model;

/** Registration status of a student. Stored by {@link #name()} in the database. */
public enum StudentStatus {
    ACTIVE("Active"),
    ON_LEAVE("On leave"),
    INACTIVE("Inactive"),
    GRADUATED("Graduated");

    private final String label;

    StudentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
