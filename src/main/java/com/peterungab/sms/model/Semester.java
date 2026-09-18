package com.peterungab.sms.model;

/** Academic term within a school year. Declaration order is chronological. */
public enum Semester {
    FIRST("1st Semester", "1st Sem"),
    SECOND("2nd Semester", "2nd Sem"),
    SUMMER("Summer", "Summer");

    private final String label;
    private final String shortLabel;

    Semester(String label, String shortLabel) {
        this.label = label;
        this.shortLabel = shortLabel;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return shortLabel;
    }

    @Override
    public String toString() {
        return label;
    }
}
