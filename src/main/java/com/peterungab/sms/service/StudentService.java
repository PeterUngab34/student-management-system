package com.peterungab.sms.service;

import com.peterungab.sms.dao.ProgramDao;
import com.peterungab.sms.dao.StudentDao;
import com.peterungab.sms.model.Program;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentFilter;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Business logic for student records: normalization, validation and uniqueness rules. */
public final class StudentService {

    /** YYYY-NNNNN, e.g. 2023-00123. */
    public static final Pattern STUDENT_NUMBER = Pattern.compile("^(19|20)\\d{2}-\\d{5}$");
    public static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    /** Letters (incl. Ñ and accents), spaces, periods, apostrophes and hyphens. */
    public static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L} .'-]*$");
    /** Philippine mobile number: 09XXXXXXXXX or +639XXXXXXXXX. */
    public static final Pattern PH_MOBILE = Pattern.compile("^(09|\\+639)\\d{9}$");

    public static final int MIN_AGE = 14;

    private final StudentDao studentDao;
    private final ProgramDao programDao;
    private final Clock clock;

    public StudentService(StudentDao studentDao, ProgramDao programDao, Clock clock) {
        this.studentDao = studentDao;
        this.programDao = programDao;
        this.clock = clock;
    }

    public List<Student> search(StudentFilter filter) {
        return studentDao.search(filter == null ? StudentFilter.all() : filter);
    }

    public List<Student> findAll() {
        return search(StudentFilter.all());
    }

    public Optional<Student> findById(int id) {
        return studentDao.findById(id);
    }

    public List<Program> programs() {
        return programDao.findAll();
    }

    public int count() {
        return studentDao.count();
    }

    public Student create(Student student) {
        Student s = normalize(student);
        validate(s, null);
        return studentDao.insert(s.withId(null));
    }

    public Student update(Student student) {
        if (student.id() == null) {
            throw new IllegalArgumentException("Cannot update a student that has not been saved");
        }
        Student s = normalize(student);
        validate(s, s.id());
        if (!studentDao.update(s)) {
            throw new BusinessRuleException("Student no longer exists. It may have been deleted.");
        }
        return s;
    }

    /** Deletes the student together with their enrollments (ON DELETE CASCADE). */
    public void delete(int studentId) {
        if (!studentDao.delete(studentId)) {
            throw new BusinessRuleException("Student no longer exists. It may have been deleted.");
        }
    }

    /** Suggests the next free student number for the current year, e.g. 2026-00422. */
    public String suggestNextStudentNumber() {
        String prefix = LocalDate.now(clock).getYear() + "-";
        int next = studentDao.findMaxStudentNumber(prefix)
                .map(max -> Integer.parseInt(max.substring(prefix.length())) + 1)
                .orElse(1);
        return prefix + String.format("%05d", next);
    }

    /** Trims and canonicalizes user input before validation. */
    static Student normalize(Student s) {
        return new Student(
                s.id(),
                trimToEmpty(s.studentNumber()),
                collapseSpaces(s.firstName()),
                collapseSpaces(s.lastName()),
                trimToEmpty(s.email()).toLowerCase(Locale.ROOT),
                emptyToNull(trimToEmpty(s.phone()).replaceAll("[\\s-]", "")),
                s.birthDate(),
                s.program(),
                s.yearLevel(),
                s.status());
    }

    private void validate(Student s, Integer existingId) {
        ValidationException.Errors errors = new ValidationException.Errors();

        if (s.studentNumber().isEmpty()) {
            errors.add("studentNumber", "Student number is required.");
        } else if (!STUDENT_NUMBER.matcher(s.studentNumber()).matches()) {
            errors.add("studentNumber", "Use the format YYYY-NNNNN, e.g. 2023-00123.");
        } else if (isTakenByAnother(studentDao.findByStudentNumber(s.studentNumber()), existingId)) {
            errors.add("studentNumber", "Student number " + s.studentNumber() + " is already in use.");
        }

        validateName(errors, "firstName", "First name", s.firstName());
        validateName(errors, "lastName", "Last name", s.lastName());

        if (s.email().isEmpty()) {
            errors.add("email", "E-mail is required.");
        } else if (s.email().length() > 100 || !EMAIL.matcher(s.email()).matches()) {
            errors.add("email", "Enter a valid e-mail address.");
        } else if (isTakenByAnother(studentDao.findByEmail(s.email()), existingId)) {
            errors.add("email", "E-mail " + s.email() + " is already registered.");
        }

        if (s.phone() != null && !PH_MOBILE.matcher(s.phone()).matches()) {
            errors.add("phone", "Use a PH mobile number, e.g. 09171234567.");
        }

        LocalDate today = LocalDate.now(clock);
        if (s.birthDate() != null) {
            if (s.birthDate().isAfter(today)) {
                errors.add("birthDate", "Birth date cannot be in the future.");
            } else if (s.birthDate().isAfter(today.minusYears(MIN_AGE))) {
                errors.add("birthDate", "Student must be at least " + MIN_AGE + " years old.");
            } else if (s.birthDate().isBefore(LocalDate.of(1900, 1, 1))) {
                errors.add("birthDate", "Enter a realistic birth date.");
            }
        }

        if (s.program() == null) {
            errors.add("program", "Select a program.");
        } else if (programDao.findById(s.program().id()).isEmpty()) {
            errors.add("program", "Program does not exist.");
        }

        if (s.yearLevel() < 1 || s.yearLevel() > 5) {
            errors.add("yearLevel", "Year level must be between 1 and 5.");
        }
        if (s.status() == null) {
            errors.add("status", "Select a status.");
        }
        errors.throwIfAny();
    }

    private static void validateName(ValidationException.Errors errors, String field, String label, String value) {
        if (value.isEmpty()) {
            errors.add(field, label + " is required.");
        } else if (value.length() > 50) {
            errors.add(field, label + " must be at most 50 characters.");
        } else if (!NAME.matcher(value).matches()) {
            errors.add(field, label + " may only contain letters, spaces, . ' and -.");
        }
    }

    private static boolean isTakenByAnother(Optional<Student> found, Integer existingId) {
        return found.isPresent() && !Objects.equals(found.get().id(), existingId);
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String collapseSpaces(String s) {
        return trimToEmpty(s).replaceAll("\\s+", " ");
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
