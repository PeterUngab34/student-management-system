package com.peterungab.sms.db;

import com.peterungab.sms.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the relational design itself: seed data, keys and CHECK / FK constraints. */
class SchemaTest {

    private Database db;

    @BeforeEach
    void setUp() {
        db = TestDatabase.create(true);
    }

    @Test
    void seedDataIsLoaded() throws SQLException {
        assertEquals(4, count("SELECT COUNT(*) FROM programs"));
        assertEquals(25, count("SELECT COUNT(*) FROM students"));
        assertEquals(10, count("SELECT COUNT(*) FROM courses"));
        assertEquals(153, count("SELECT COUNT(*) FROM enrollments"));
        assertEquals(0, count("SELECT COUNT(*) FROM enrollments WHERE status = 'COMPLETED' AND grade IS NULL"));
    }

    @Test
    void initializerIsIdempotent() throws SQLException {
        assertFalse(DatabaseInitializer.initializeIfNeeded(db, true), "schema already exists");
        assertEquals(25, count("SELECT COUNT(*) FROM students"));

        Database empty = Database.h2InMemory("empty_" + System.nanoTime());
        assertTrue(DatabaseInitializer.initializeIfNeeded(empty, false));
        try (Connection c = empty.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM students")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void checkConstraintRejectsGradeOutsideScale() {
        assertThrows(SQLException.class, () -> exec(
                "UPDATE enrollments SET grade = 6.00 WHERE status = 'COMPLETED'"));
    }

    @Test
    void checkConstraintRequiresGradeForCompletedEnrollment() {
        assertThrows(SQLException.class, () -> exec(
                "UPDATE enrollments SET grade = NULL WHERE status = 'COMPLETED'"));
    }

    @Test
    void uniqueConstraintRejectsDuplicateEnrollment() {
        assertThrows(SQLException.class, () -> exec("""
                INSERT INTO enrollments (student_id, course_id, school_year, semester, grade, status)
                SELECT student_id, course_id, school_year, semester, grade, status FROM enrollments
                WHERE enrollment_id = (SELECT MIN(enrollment_id) FROM enrollments)
                """));
    }

    @Test
    void deletingStudentCascadesToEnrollments() throws SQLException {
        int before = count("SELECT COUNT(*) FROM enrollments e JOIN students s ON s.student_id = e.student_id "
                + "WHERE s.student_number = '2023-00123'");
        assertTrue(before > 0);
        exec("DELETE FROM students WHERE student_number = '2023-00123'");
        assertEquals(153 - before, count("SELECT COUNT(*) FROM enrollments"));
    }

    @Test
    void foreignKeyPreventsDeletingCourseWithEnrollments() {
        assertThrows(SQLException.class, () -> exec("DELETE FROM courses WHERE course_code = 'CPE 201'"));
    }

    private int count(String sql) throws SQLException {
        try (Connection c = db.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
