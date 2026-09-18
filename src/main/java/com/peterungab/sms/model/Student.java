package com.peterungab.sms.model;

import java.time.LocalDate;

/**
 * A student record. {@code id} is {@code null} for records that have not been saved yet.
 */
public record Student(
        Integer id,
        String studentNumber,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate birthDate,
        Program program,
        int yearLevel,
        StudentStatus status) {

    public String fullName() {
        return firstName + " " + lastName;
    }

    /** "Last, First" - the conventional ordering for class lists. */
    public String sortableName() {
        return lastName + ", " + firstName;
    }

    public String initials() {
        return (firstName.isEmpty() ? "" : firstName.substring(0, 1))
                + (lastName.isEmpty() ? "" : lastName.substring(0, 1));
    }

    public Student withId(Integer newId) {
        return new Student(newId, studentNumber, firstName, lastName, email, phone, birthDate, program, yearLevel, status);
    }

    public static String yearLevelLabel(int yearLevel) {
        return switch (yearLevel) {
            case 1 -> "1st Year";
            case 2 -> "2nd Year";
            case 3 -> "3rd Year";
            default -> yearLevel + "th Year";
        };
    }
}
