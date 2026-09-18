package com.peterungab.sms.dao.jdbc;

import com.peterungab.sms.TestDatabase;
import com.peterungab.sms.dao.DataAccessException;
import com.peterungab.sms.db.Database;
import com.peterungab.sms.model.Course;
import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.EnrollmentFilter;
import com.peterungab.sms.model.EnrollmentStatus;
import com.peterungab.sms.model.Program;
import com.peterungab.sms.model.Semester;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentFilter;
import com.peterungab.sms.model.StudentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the JDBC DAOs directly (no service layer) against the seeded H2 database. */
class JdbcDaoTest {

    private JdbcStudentDao students;
    private JdbcCourseDao courses;
    private JdbcEnrollmentDao enrollments;
    private JdbcProgramDao programs;

    @BeforeEach
    void setUp() {
        Database db = TestDatabase.create(true);
        students = new JdbcStudentDao(db);
        courses = new JdbcCourseDao(db);
        enrollments = new JdbcEnrollmentDao(db);
        programs = new JdbcProgramDao(db);
    }

    @Test
    void studentRoundTrip() {
        Program bsit = programs.findAll().stream().filter(p -> p.code().equals("BSIT")).findFirst().orElseThrow();
        Student saved = students.insert(new Student(null, "2026-00999", "Liza", "Soberano", "liza@example.edu.ph",
                null, LocalDate.of(2007, 1, 4), bsit, 1, StudentStatus.ACTIVE));
        assertNotNull(saved.id());

        Student loaded = students.findById(saved.id()).orElseThrow();
        assertEquals("Soberano", loaded.lastName());
        assertEquals(LocalDate.of(2007, 1, 4), loaded.birthDate());
        assertNull(loaded.phone());
        assertEquals("BS Information Technology", loaded.program().name());

        assertTrue(students.update(new Student(saved.id(), "2026-00999", "Liza", "Soberano", "liza@example.edu.ph",
                "09171234567", null, bsit, 2, StudentStatus.INACTIVE)));
        Student updated = students.findById(saved.id()).orElseThrow();
        assertEquals(StudentStatus.INACTIVE, updated.status());
        assertEquals("09171234567", updated.phone());
        assertNull(updated.birthDate());

        assertTrue(students.delete(saved.id()));
        assertFalse(students.delete(saved.id()), "second delete finds nothing");
    }

    @Test
    void findsByNaturalKeys() {
        assertEquals("Santos", students.findByStudentNumber("2023-00123").orElseThrow().lastName());
        assertTrue(students.findByEmail("MARIA.SANTOS@student.example.edu.ph").isPresent());
        assertEquals("2026-00421", students.findMaxStudentNumber("2026-").orElseThrow());
        assertTrue(students.findMaxStudentNumber("2030-").isEmpty());
        assertEquals("Operating Systems", courses.findByCode("cpe 321").orElseThrow().title());
    }

    @Test
    void combinedFilterAndSort() {
        Program bscpe = programs.findAll().stream().filter(p -> p.code().equals("BSCpE")).findFirst().orElseThrow();
        List<Student> result = students.search(StudentFilter.all()
                .withProgramId(bscpe.id())
                .withStatus(StudentStatus.ACTIVE)
                .withKeyword("a")
                .sortedBy(StudentFilter.SortField.YEAR_LEVEL, false));
        assertFalse(result.isEmpty());
        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i - 1).yearLevel() >= result.get(i).yearLevel(), "sorted by year, descending");
        }
        assertTrue(result.stream().allMatch(s -> s.status() == StudentStatus.ACTIVE && s.program().id() == bscpe.id()));
    }

    @Test
    void aggregateQueries() {
        Map<String, Integer> byProgram = students.countByProgram();
        assertEquals(Map.of("BSCpE", 10, "BSCS", 6, "BSIT", 5, "BSECE", 4), byProgram);

        Map<Integer, Integer> perCourse = courses.countEnrollmentsByCourse();
        assertEquals(153, perCourse.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(List.of("2026-2027", "2025-2026", "2024-2025", "2023-2024"), enrollments.findSchoolYears());
    }

    @Test
    void enrollmentLifecycle() {
        Student s = students.findByStudentNumber("2026-00415").orElseThrow();
        Course c = courses.findByCode("ECE 211").orElseThrow();
        assertFalse(enrollments.exists(s.id(), c.id(), "2026-2027", Semester.SECOND));

        int id = enrollments.insert(s.id(), c.id(), "2026-2027", Semester.SECOND);
        assertTrue(enrollments.exists(s.id(), c.id(), "2026-2027", Semester.SECOND));
        assertEquals(EnrollmentStatus.ENROLLED, enrollments.findById(id).orElseThrow().status());

        assertTrue(enrollments.updateGrade(id, new BigDecimal("1.75"), EnrollmentStatus.COMPLETED));
        Enrollment graded = enrollments.findById(id).orElseThrow();
        assertEquals(new BigDecimal("1.75"), graded.grade());
        assertEquals("2nd Sem 2026-2027", graded.term());

        // the database itself rejects a duplicate enrollment (unique constraint)
        assertThrows(DataAccessException.class, () -> enrollments.insert(s.id(), c.id(), "2026-2027", Semester.SECOND));

        assertTrue(enrollments.delete(id));
        assertTrue(enrollments.findById(id).isEmpty());
    }

    @Test
    void enrollmentSearchOrdersNewestTermFirst() {
        List<Enrollment> all = enrollments.search(EnrollmentFilter.all());
        assertEquals(153, all.size());
        assertEquals("2026-2027", all.get(0).schoolYear());
        assertEquals("2023-2024", all.get(all.size() - 1).schoolYear());
        assertTrue(enrollments.search(new EnrollmentFilter("100%", null, null, null)).isEmpty());
    }

    @Test
    void courseCrud() {
        Course saved = courses.insert(new Course(null, "CPE 499", "Thesis 2", 3, null));
        assertTrue(courses.update(new Course(saved.id(), "CPE 499", "Thesis II", 3, "Capstone")));
        assertEquals("Thesis II", courses.findById(saved.id()).orElseThrow().title());
        assertEquals(11, courses.count());
        assertTrue(courses.delete(saved.id()));
        assertEquals(10, courses.count());
    }
}
