package com.peterungab.sms.service;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.TestDatabase;
import com.peterungab.sms.model.Course;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseServiceTest {

    private CourseService service;

    @BeforeEach
    void setUp() {
        AppContext ctx = TestDatabase.context(true);
        service = ctx.courses();
    }

    @Test
    void createsCourseWithNormalizedCode() {
        Course saved = service.create(new Course(null, " cpe411 ", "  Software   Engineering ", 3, " "));
        assertEquals("CPE 411", saved.code());
        assertEquals("Software Engineering", saved.title());
        assertEquals(null, saved.description());
        assertEquals(11, service.count());
    }

    @Test
    void updatesCourse() {
        Course c = service.search("CS 221").get(0);
        service.update(new Course(c.id(), c.code(), "Database Systems", 4, c.description()));
        Course reloaded = service.findById(c.id()).orElseThrow();
        assertEquals("Database Systems", reloaded.title());
        assertEquals(4, reloaded.units());
    }

    @Test
    void deletesCourseWithoutEnrollments() {
        Course c = service.create(new Course(null, "CPE 499", "Special Topics", 3, null));
        service.delete(c.id());
        assertTrue(service.findById(c.id()).isEmpty());
    }

    @Test
    void refusesToDeleteCourseWithEnrollments() {
        Course c = service.search("CPE 201").get(0);
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> service.delete(c.id()));
        assertTrue(e.getMessage().startsWith("CPE 201 has"));
    }

    @Test
    void rejectsDuplicateCodeAndInvalidFields() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create(new Course(null, "cpe 201", "", 9, null)));
        assertTrue(e.hasError("code"), "duplicate code (case-insensitive)");
        assertTrue(e.hasError("title"));
        assertTrue(e.hasError("units"));
    }

    @Test
    void rejectsMalformedCode() {
        assertTrue(assertThrows(ValidationException.class,
                () -> service.create(new Course(null, "301", "Nope", 3, null))).hasError("code"));
    }

    @Test
    void searchesByCodeOrTitle() {
        assertEquals(4, service.search("cpe 3").size() + service.search("cpe 4").size());
        assertEquals(1, service.search("database").size());
        assertEquals(10, service.search("").size());
    }
}
