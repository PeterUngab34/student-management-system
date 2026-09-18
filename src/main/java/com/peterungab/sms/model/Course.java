package com.peterungab.sms.model;

/**
 * A course in the catalogue. {@code units} are the credit units used to weight the GPA.
 */
public record Course(Integer id, String code, String title, int units, String description) {

    public Course withId(Integer newId) {
        return new Course(newId, code, title, units, description);
    }

    @Override
    public String toString() {
        return code + " - " + title;
    }
}
