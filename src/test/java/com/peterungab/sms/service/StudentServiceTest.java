package com.peterungab.sms.service;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.TestDatabase;
import com.peterungab.sms.model.Program;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentFilter;
import com.peterungab.sms.model.StudentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentServiceTest {

    private AppContext ctx;
    private StudentService service;
    private Program bscpe;

    @BeforeEach
    void setUp() {
        ctx = TestDatabase.context(true);
        service = ctx.students();
        bscpe = service.programs().stream().filter(p -> p.code().equals("BSCpE")).findFirst().orElseThrow();
    }

    private Student newStudent(String number, String email) {
        return new Student(null, number, "Juan", "Dela Cruz", email, "0917 123 4567",
                LocalDate.of(2007, 3, 14), bscpe, 1, StudentStatus.ACTIVE);
    }

    // ----- CRUD ---------------------------------------------------------------------------

    @Test
    void createsAndReadsBackStudent() {
        Student saved = service.create(newStudent("2026-00500", "Juan.DelaCruz@Student.Example.edu.ph"));

        assertNotNull(saved.id());
        Student loaded = service.findById(saved.id()).orElseThrow();
        assertEquals("2026-00500", loaded.studentNumber());
        assertEquals("juan.delacruz@student.example.edu.ph", loaded.email(), "e-mail is normalized to lower case");
        assertEquals("09171234567", loaded.phone(), "phone spaces are removed");
        assertEquals("BSCpE", loaded.program().code());
        assertEquals(26, service.count());
    }

    @Test
    void updatesStudent() {
        Student s = service.search(StudentFilter.all().withKeyword("2023-00123")).get(0);
        Student changed = new Student(s.id(), s.studentNumber(), "Maria Clara", s.lastName(), s.email(), s.phone(),
                s.birthDate(), s.program(), 5, StudentStatus.ON_LEAVE);

        service.update(changed);

        Student reloaded = service.findById(s.id()).orElseThrow();
        assertEquals("Maria Clara", reloaded.firstName());
        assertEquals(5, reloaded.yearLevel());
        assertEquals(StudentStatus.ON_LEAVE, reloaded.status());
    }

    @Test
    void deletesStudentAndTheirEnrollments() {
        Student s = service.search(StudentFilter.all().withKeyword("2023-00123")).get(0);
        int enrollmentsBefore = ctx.enrollments().count();
        int own = ctx.enrollments().academicRecord(s.id()).enrollments().size();

        service.delete(s.id());

        assertTrue(service.findById(s.id()).isEmpty());
        assertEquals(enrollmentsBefore - own, ctx.enrollments().count());
    }

    @Test
    void suggestsNextStudentNumberForCurrentYear() {
        assertEquals("2026-00422", service.suggestNextStudentNumber());
    }

    // ----- validation ---------------------------------------------------------------------

    @Test
    void rejectsDuplicateStudentNumber() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create(newStudent("2023-00123", "new.person@student.example.edu.ph")));
        assertTrue(e.hasError("studentNumber"));
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create(newStudent("2026-00501", "MARIA.SANTOS@student.example.edu.ph")));
        assertTrue(e.hasError("email"));
    }

    @Test
    void updatingAStudentKeepsTheirOwnNumberAndEmail() {
        Student s = service.search(StudentFilter.all().withKeyword("2023-00123")).get(0);
        service.update(s); // same number + email must not count as duplicates of itself
    }

    @Test
    void reportsEveryInvalidField() {
        Student bad = new Student(null, "23-123", "", "D3la Cruz", "not-an-email", "12345",
                LocalDate.now(TestDatabase.CLOCK).plusDays(1), null, 7, null);

        ValidationException e = assertThrows(ValidationException.class, () -> service.create(bad));

        assertEquals(List.of("studentNumber", "firstName", "lastName", "email", "phone", "birthDate",
                "program", "yearLevel", "status"), List.copyOf(e.errors().keySet()));
    }

    @Test
    void acceptsFilipinoNamesWithEnyeAndHyphens() {
        Student s = new Student(null, "2026-00510", "Ma. Luisa", "Peñaflor-De los Santos",
                "luisa.penaflor@student.example.edu.ph", "+639171234567", null, bscpe, 1, StudentStatus.ACTIVE);
        assertNotNull(service.create(s).id());
    }

    @Test
    void rejectsStudentYoungerThanMinimumAge() {
        Student s = newStudent("2026-00502", "young@student.example.edu.ph");
        Student tooYoung = new Student(null, s.studentNumber(), s.firstName(), s.lastName(), s.email(), s.phone(),
                LocalDate.of(2015, 1, 1), bscpe, 1, StudentStatus.ACTIVE);
        assertTrue(assertThrows(ValidationException.class, () -> service.create(tooYoung)).hasError("birthDate"));
    }

    // ----- search, filter & sort ----------------------------------------------------------

    @Test
    void searchesByNameStudentNumberAndEmail() {
        assertEquals(1, service.search(StudentFilter.all().withKeyword("santos")).size());
        assertEquals(1, service.search(StudentFilter.all().withKeyword("maria santos")).size());
        assertEquals(1, service.search(StudentFilter.all().withKeyword("2021-00042")).size());
        assertEquals(1, service.search(StudentFilter.all().withKeyword("kevin.tan@")).size());
        assertEquals("Ibañez", service.search(StudentFilter.all().withKeyword("ibañez")).get(0).lastName());
    }

    @Test
    void searchTreatsWildcardsAndQuotesLiterally() {
        assertEquals(0, service.search(StudentFilter.all().withKeyword("%")).size());
        assertEquals(0, service.search(StudentFilter.all().withKeyword("_")).size());
        // classic injection payload is just a (non-matching) search term
        assertEquals(0, service.search(StudentFilter.all().withKeyword("' OR '1'='1")).size());
        assertEquals(25, service.count());
    }

    @Test
    void filtersByProgramYearLevelAndStatus() {
        List<Student> cpe = service.search(StudentFilter.all().withProgramId(bscpe.id()));
        assertEquals(10, cpe.size());
        assertTrue(cpe.stream().allMatch(s -> s.program().code().equals("BSCpE")));

        List<Student> fifthYearCpe = service.search(StudentFilter.all().withProgramId(bscpe.id()).withYearLevel(5));
        assertEquals(3, fifthYearCpe.size());

        List<Student> graduated = service.search(StudentFilter.all().withStatus(StudentStatus.GRADUATED));
        assertEquals(1, graduated.size());
        assertEquals("Domingo", graduated.get(0).lastName());
    }

    @Test
    void sortsByRequestedField() {
        List<Student> byNumberDesc = service.search(
                StudentFilter.all().sortedBy(StudentFilter.SortField.STUDENT_NUMBER, false));
        assertEquals("2026-00421", byNumberDesc.get(0).studentNumber());
        assertEquals("2021-00042", byNumberDesc.get(byNumberDesc.size() - 1).studentNumber());

        List<Student> byName = service.search(StudentFilter.all());
        assertEquals("Aguilar", byName.get(0).lastName());
    }
}
