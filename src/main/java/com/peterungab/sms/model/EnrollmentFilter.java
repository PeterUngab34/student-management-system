package com.peterungab.sms.model;

/**
 * Search criteria for the enrollment list. Every criterion is optional ({@code null} = any).
 *
 * @param keyword    matched against student number, student name, course code and course title
 * @param courseId   only enrollments in this course
 * @param schoolYear only this school year, e.g. "2025-2026"
 * @param semester   only this semester
 */
public record EnrollmentFilter(String keyword, Integer courseId, String schoolYear, Semester semester) {

    public static EnrollmentFilter all() {
        return new EnrollmentFilter(null, null, null, null);
    }
}
