package com.peterungab.sms.service;

import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.GradeScale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Optional;

/**
 * GPA arithmetic on the Philippine 1.00 - 5.00 scale.
 * <p>
 * GPA = &Sigma;(grade &times; units) / &Sigma;(units), over graded (COMPLETED) enrollments only.
 * In-progress and dropped courses are ignored; failed courses (5.00) count, as they do on a
 * real transcript. The result is rounded half-up to two decimals.
 */
public final class GradeCalculator {

    private GradeCalculator() {
    }

    public static Optional<BigDecimal> gpa(Collection<Enrollment> enrollments) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        int totalUnits = 0;
        for (Enrollment e : enrollments) {
            if (e.isGraded()) {
                weightedSum = weightedSum.add(e.grade().multiply(BigDecimal.valueOf(e.units())));
                totalUnits += e.units();
            }
        }
        if (totalUnits == 0) {
            return Optional.empty();
        }
        return Optional.of(weightedSum.divide(BigDecimal.valueOf(totalUnits), 2, RoundingMode.HALF_UP));
    }

    /** Units of courses completed with a passing grade (3.00 or better). */
    public static int unitsEarned(Collection<Enrollment> enrollments) {
        return enrollments.stream()
                .filter(e -> e.isGraded() && GradeScale.isPassing(e.grade()))
                .mapToInt(Enrollment::units)
                .sum();
    }

    /** Simple average of several GPAs (e.g. across students), rounded to two decimals. */
    public static Optional<BigDecimal> average(Collection<BigDecimal> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP));
    }
}
