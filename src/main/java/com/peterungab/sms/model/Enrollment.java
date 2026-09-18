package com.peterungab.sms.model;

import java.math.BigDecimal;

/**
 * A student's enrollment in a course for a specific term, including the final grade.
 * Student and course display fields are denormalized from the joined rows for convenience.
 */
public record Enrollment(
        Integer id,
        int studentId,
        String studentNumber,
        String studentName,
        int courseId,
        String courseCode,
        String courseTitle,
        int units,
        String schoolYear,
        Semester semester,
        BigDecimal grade,
        EnrollmentStatus status) {

    /** e.g. "1st Sem 2025-2026". */
    public String term() {
        return semester.shortLabel() + " " + schoolYear;
    }

    /** Sort key that orders terms chronologically, e.g. "2025-2026#0". */
    public String termKey() {
        return schoolYear + "#" + semester.ordinal();
    }

    public boolean isGraded() {
        return status == EnrollmentStatus.COMPLETED && grade != null;
    }

    public String remarks() {
        if (status == EnrollmentStatus.DROPPED) {
            return "Dropped";
        }
        if (grade == null) {
            return "In progress";
        }
        return GradeScale.isPassing(grade) ? "Passed" : "Failed";
    }
}
