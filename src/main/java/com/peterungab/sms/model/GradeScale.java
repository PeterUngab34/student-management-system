package com.peterungab.sms.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * The Philippine 1.00 - 5.00 grading scale used by most state and private universities.
 * <pre>
 *   1.00            Excellent
 *   1.25 - 1.50     Very good
 *   1.75 - 2.25     Good
 *   2.50 - 2.75     Satisfactory
 *   3.00            Passing
 *   5.00            Failed
 * </pre>
 * Lower is better, so a "higher" GPA in the everyday sense is a numerically smaller value.
 */
public final class GradeScale {

    public static final BigDecimal HIGHEST = new BigDecimal("1.00");
    public static final BigDecimal PASSING = new BigDecimal("3.00");
    public static final BigDecimal FAILED = new BigDecimal("5.00");

    /** Every grade that may be recorded, best to worst. */
    public static final List<BigDecimal> VALID_GRADES = List.of(
            new BigDecimal("1.00"), new BigDecimal("1.25"), new BigDecimal("1.50"),
            new BigDecimal("1.75"), new BigDecimal("2.00"), new BigDecimal("2.25"),
            new BigDecimal("2.50"), new BigDecimal("2.75"), PASSING, FAILED);

    private GradeScale() {
    }

    public static boolean isValid(BigDecimal grade) {
        return grade != null && VALID_GRADES.stream().anyMatch(g -> g.compareTo(grade) == 0);
    }

    public static boolean isPassing(BigDecimal grade) {
        return grade != null && grade.compareTo(PASSING) <= 0;
    }

    public static String describe(BigDecimal grade) {
        if (grade == null) {
            return "";
        }
        if (grade.compareTo(HIGHEST) == 0) {
            return "Excellent";
        }
        if (grade.compareTo(new BigDecimal("1.50")) <= 0) {
            return "Very good";
        }
        if (grade.compareTo(new BigDecimal("2.25")) <= 0) {
            return "Good";
        }
        if (grade.compareTo(new BigDecimal("2.75")) <= 0) {
            return "Satisfactory";
        }
        return isPassing(grade) ? "Passing" : "Failed";
    }

    /** Label used for grade values in tables and charts. */
    public static String format(BigDecimal grade) {
        return grade == null ? "—" : grade.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
