package com.peterungab.sms.service;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.TestDatabase;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnrollmentServiceTest {

    private AppContext ctx;
    private EnrollmentService service;
    private Student student;
    private Course oop;
    private Course dsa;
    private Course circuits;

    /** Starts from an empty schema so every GPA figure below is fully controlled by the test. */
    @BeforeEach
    void setUp() {
        ctx = TestDatabase.context(false);
        service = ctx.enrollments();
        try (var c = ctx.database().getConnection(); var st = c.createStatement()) {
            st.executeUpdate("INSERT INTO programs (code, name, department) VALUES "
                    + "('BSCpE', 'BS Computer Engineering', 'College of Engineering')");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        Program bscpe = ctx.students().programs().get(0);
        student = ctx.students().create(new Student(null, "2024-00001", "Ana", "Reyes", "ana.reyes@example.edu.ph",
                null, LocalDate.of(2006, 5, 1), bscpe, 3, StudentStatus.ACTIVE));
        oop = ctx.courses().create(new Course(null, "CS 211", "Object-Oriented Programming", 3, null));
        dsa = ctx.courses().create(new Course(null, "CPE 201", "Data Structures and Algorithms", 3, null));
        circuits = ctx.courses().create(new Course(null, "ECE 211", "Electronic Circuits", 4, null));
    }

    @Test
    void enrollsActiveStudent() {
        Enrollment e = service.enroll(student.id(), oop.id(), "2026-2027", Semester.FIRST);
        assertEquals(EnrollmentStatus.ENROLLED, e.status());
        assertNull(e.grade());
        assertEquals("CS 211", e.courseCode());
        assertEquals("Ana Reyes", e.studentName());
        assertEquals("1st Sem 2026-2027", e.term());
    }

    @Test
    void rejectsDuplicateEnrollmentInSameTerm() {
        service.enroll(student.id(), oop.id(), "2026-2027", Semester.FIRST);

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.enroll(student.id(), oop.id(), "2026-2027", Semester.FIRST));
        assertTrue(e.hasError("course"));
        assertTrue(e.getMessage().contains("already enrolled"));
        assertEquals(1, service.count());
    }

    @Test
    void allowsRetakingCourseInAnotherTerm() {
        Enrollment failed = service.enroll(student.id(), oop.id(), "2025-2026", Semester.FIRST);
        service.recordGrade(failed.id(), new BigDecimal("5.00"));
        service.enroll(student.id(), oop.id(), "2025-2026", Semester.SECOND);
        assertEquals(2, service.count());
    }

    @Test
    void rejectsEnrollingInactiveStudent() {
        ctx.students().update(new Student(student.id(), student.studentNumber(), student.firstName(),
                student.lastName(), student.email(), null, student.birthDate(), student.program(), 3,
                StudentStatus.GRADUATED));
        assertTrue(assertThrows(ValidationException.class,
                () -> service.enroll(student.id(), oop.id(), "2026-2027", Semester.FIRST)).hasError("student"));
    }

    @Test
    void validatesSchoolYear() {
        assertTrue(EnrollmentService.isValidSchoolYear("2025-2026"));
        assertTrue(!EnrollmentService.isValidSchoolYear("2025-2027"));
        assertTrue(!EnrollmentService.isValidSchoolYear("25-26"));
        assertTrue(assertThrows(ValidationException.class,
                () -> service.enroll(student.id(), oop.id(), "2026", Semester.FIRST)).hasError("schoolYear"));
    }

    @Test
    void recordingGradeCompletesEnrollmentAndClearingReopensIt() {
        Enrollment e = service.enroll(student.id(), oop.id(), "2026-2027", Semester.FIRST);

        Enrollment graded = service.recordGrade(e.id(), new BigDecimal("1.5"));
        assertEquals(EnrollmentStatus.COMPLETED, graded.status());
        assertEquals(new BigDecimal("1.50"), graded.grade());
        assertEquals("Passed", graded.remarks());

        Enrollment cleared = service.recordGrade(e.id(), null);
        assertEquals(EnrollmentStatus.ENROLLED, cleared.status());
        assertNull(cleared.grade());
    }

    @Test
    void rejectsGradeOutsidePhilippineScale() {
        Enrollment e = service.enroll(student.id(), oop.id(), "2026-2027", Semester.FIRST);
        for (String bad : List.of("0.75", "1.10", "3.25", "4.00", "5.25")) {
            assertThrows(ValidationException.class, () -> service.recordGrade(e.id(), new BigDecimal(bad)), bad);
        }
    }

    @Test
    void dropRules() {
        Enrollment e = service.enroll(student.id(), oop.id(), "2026-2027", Semester.FIRST);
        assertEquals(EnrollmentStatus.DROPPED, service.drop(e.id()).status());
        assertThrows(BusinessRuleException.class, () -> service.recordGrade(e.id(), new BigDecimal("2.00")));

        Enrollment graded = service.enroll(student.id(), dsa.id(), "2026-2027", Semester.FIRST);
        service.recordGrade(graded.id(), new BigDecimal("2.00"));
        assertThrows(BusinessRuleException.class, () -> service.drop(graded.id()));
    }

    @Test
    void computesUnitWeightedGpaFromDatabase() {
        // 3 units x 1.25 + 3 units x 2.00 + 4 units x 1.75 = 3.75 + 6.00 + 7.00 = 16.75 / 10 = 1.675 -> 1.68
        grade(oop, "2025-2026", Semester.FIRST, "1.25");
        grade(dsa, "2025-2026", Semester.FIRST, "2.00");
        grade(circuits, "2025-2026", Semester.SECOND, "1.75");
        service.enroll(student.id(), oop.id(), "2026-2027", Semester.FIRST); // retake, in progress: ignored

        AcademicRecord record = service.academicRecord(student.id());

        assertEquals(new BigDecimal("1.68"), record.gpa().orElseThrow());
        assertEquals(10, record.unitsEarned());
        assertEquals(4, record.enrollments().size());
        assertEquals(List.of("1st Sem 2025-2026", "2nd Sem 2025-2026", "1st Sem 2026-2027"),
                record.terms().stream().map(AcademicRecord.TermSummary::term).toList());
        assertEquals(new BigDecimal("1.63"), record.terms().get(0).gpa().orElseThrow()); // (3.75+6)/6 = 1.625
        assertTrue(record.terms().get(2).gpa().isEmpty());

        Map<Integer, BigDecimal> all = service.gpaByStudent();
        assertEquals(new BigDecimal("1.68"), all.get(student.id()));
    }

    @Test
    void failedCoursesCountTowardGpaButNotUnitsEarned() {
        grade(oop, "2025-2026", Semester.FIRST, "5.00");
        grade(dsa, "2025-2026", Semester.FIRST, "1.00");
        AcademicRecord record = service.academicRecord(student.id());
        assertEquals(new BigDecimal("3.00"), record.gpa().orElseThrow());
        assertEquals(3, record.unitsEarned());
    }

    @Test
    void filtersEnrollmentsByTermCourseAndKeyword() {
        grade(oop, "2025-2026", Semester.FIRST, "1.25");
        service.enroll(student.id(), dsa.id(), "2026-2027", Semester.FIRST);
        service.enroll(student.id(), circuits.id(), "2026-2027", Semester.FIRST);

        assertEquals(2, service.search(new EnrollmentFilter(null, null, "2026-2027", Semester.FIRST)).size());
        assertEquals(1, service.search(new EnrollmentFilter(null, oop.id(), null, null)).size());
        assertEquals(1, service.search(new EnrollmentFilter("circuits", null, null, null)).size());
        assertEquals(3, service.search(new EnrollmentFilter("ana reyes", null, null, null)).size());
        assertEquals(List.of("2026-2027", "2025-2026"), service.schoolYears());
    }

    @Test
    void derivesCurrentTermFromDate() {
        assertEquals("2026-2027", EnrollmentService.currentSchoolYear(LocalDate.of(2026, 9, 18)));
        assertEquals(Semester.FIRST, EnrollmentService.currentSemester(LocalDate.of(2026, 9, 18)));
        assertEquals("2025-2026", EnrollmentService.currentSchoolYear(LocalDate.of(2026, 2, 1)));
        assertEquals(Semester.SECOND, EnrollmentService.currentSemester(LocalDate.of(2026, 2, 1)));
        assertEquals(Semester.SUMMER, EnrollmentService.currentSemester(LocalDate.of(2026, 6, 15)));
    }

    @Test
    void seededDatabaseHasConsistentGpas() {
        AppContext seeded = TestDatabase.context(true);
        Map<Integer, BigDecimal> gpas = seeded.enrollments().gpaByStudent();
        assertTrue(gpas.size() >= 20);
        for (Student s : seeded.students().search(StudentFilter.all())) {
            AcademicRecord r = seeded.enrollments().academicRecord(s.id());
            assertEquals(r.gpa().orElse(null), gpas.get(s.id()), s.fullName());
        }
    }

    private void grade(Course course, String sy, Semester sem, String grade) {
        Enrollment e = service.enroll(student.id(), course.id(), sy, sem);
        service.recordGrade(e.id(), new BigDecimal(grade));
    }
}
