package com.peterungab.sms.service;

import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.EnrollmentStatus;
import com.peterungab.sms.model.GradeScale;
import com.peterungab.sms.model.Semester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeCalculatorTest {

    private static Enrollment e(int units, String grade, EnrollmentStatus status) {
        return new Enrollment(1, 1, "2024-00001", "Ana Reyes", 1, "CS 211", "OOP", units,
                "2025-2026", Semester.FIRST, grade == null ? null : new BigDecimal(grade), status);
    }

    @Test
    void weightsGradesByUnits() {
        // (1.00*3 + 2.00*3 + 3.00*4) / 10 = 21 / 10 = 2.10
        List<Enrollment> list = List.of(
                e(3, "1.00", EnrollmentStatus.COMPLETED),
                e(3, "2.00", EnrollmentStatus.COMPLETED),
                e(4, "3.00", EnrollmentStatus.COMPLETED));
        assertEquals(new BigDecimal("2.10"), GradeCalculator.gpa(list).orElseThrow());
    }

    @Test
    void ignoresInProgressAndDroppedCourses() {
        List<Enrollment> list = List.of(
                e(3, "1.50", EnrollmentStatus.COMPLETED),
                e(3, null, EnrollmentStatus.ENROLLED),
                e(3, null, EnrollmentStatus.DROPPED));
        assertEquals(new BigDecimal("1.50"), GradeCalculator.gpa(list).orElseThrow());
        assertEquals(3, GradeCalculator.unitsEarned(list));
    }

    @Test
    void isEmptyWithoutGradedCourses() {
        assertTrue(GradeCalculator.gpa(List.of()).isEmpty());
        assertTrue(GradeCalculator.gpa(List.of(e(3, null, EnrollmentStatus.ENROLLED))).isEmpty());
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        // (1.25*3 + 1.50*3 + 1.50*2) / 8 = 11.25 / 8 = 1.40625 -> 1.41
        List<Enrollment> list = List.of(
                e(3, "1.25", EnrollmentStatus.COMPLETED),
                e(3, "1.50", EnrollmentStatus.COMPLETED),
                e(2, "1.50", EnrollmentStatus.COMPLETED));
        assertEquals(new BigDecimal("1.41"), GradeCalculator.gpa(list).orElseThrow());
    }

    @Test
    void averagesGpas() {
        assertEquals(new BigDecimal("1.83"), GradeCalculator.average(List.of(
                new BigDecimal("1.50"), new BigDecimal("2.00"), new BigDecimal("2.00"))).orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({
            "1.00, true, true, Excellent",
            "1.25, true, true, Very good",
            "2.00, true, true, Good",
            "2.75, true, true, Satisfactory",
            "3.00, true, true, Passing",
            "5.00, true, false, Failed",
            "4.00, false, false, Failed",
            "1.30, false, true, Very good"})
    void gradeScale(String grade, boolean valid, boolean passing, String description) {
        BigDecimal g = new BigDecimal(grade);
        assertEquals(valid, GradeScale.isValid(g));
        assertEquals(passing, GradeScale.isPassing(g));
        assertEquals(description, GradeScale.describe(g));
    }
}
