package com.peterungab.sms.service;

import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.Student;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * A student's transcript: every enrollment plus computed GPA figures.
 *
 * @param student     the student
 * @param enrollments all enrollments, newest term first
 * @param gpa         cumulative GPA (empty if nothing has been graded yet)
 * @param unitsEarned units passed
 * @param terms       per-term summary, oldest first
 */
public record AcademicRecord(
        Student student,
        List<Enrollment> enrollments,
        Optional<BigDecimal> gpa,
        int unitsEarned,
        List<TermSummary> terms) {

    /** GPA and load for one term. */
    public record TermSummary(String term, int courses, int units, Optional<BigDecimal> gpa) {
    }
}
