package com.peterungab.sms.model;

/** Lifecycle of an enrollment: in progress, graded, or dropped. */
public enum EnrollmentStatus {
    ENROLLED("Enrolled"),
    COMPLETED("Completed"),
    DROPPED("Dropped");

    private final String label;

    EnrollmentStatus(String label) {
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
