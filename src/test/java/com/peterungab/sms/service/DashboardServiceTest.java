package com.peterungab.sms.service;

import com.peterungab.sms.TestDatabase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServiceTest {

    @Test
    void aggregatesSeedData() {
        DashboardService.Stats stats = TestDatabase.context(true).dashboard().load();

        assertEquals(25, stats.totalStudents());
        assertEquals(22, stats.activeStudents());
        assertEquals(10, stats.totalCourses());
        assertEquals(153, stats.totalEnrollments());
        assertEquals("1st Sem 2026-2027", stats.currentTerm());
        assertTrue(stats.currentTermEnrollments() > 0);

        assertEquals(10, stats.gradeDistribution().size(), "one bucket per valid grade");
        int graded = stats.gradeDistribution().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(116, graded);

        assertEquals(List.of("BSCpE", "BSCS", "BSIT", "BSECE"), List.copyOf(stats.studentsByProgram().keySet()));
        assertEquals(25, stats.studentsByProgram().values().stream().mapToInt(Integer::intValue).sum());

        BigDecimal avg = stats.averageGpa().orElseThrow();
        assertTrue(avg.compareTo(BigDecimal.ONE) >= 0 && avg.compareTo(new BigDecimal("3.00")) <= 0);

        assertEquals(5, stats.topStudents().size());
        for (int i = 1; i < stats.topStudents().size(); i++) {
            assertTrue(stats.topStudents().get(i - 1).gpa().compareTo(stats.topStudents().get(i).gpa()) <= 0,
                    "ranked best (lowest) GPA first");
        }
    }
}
