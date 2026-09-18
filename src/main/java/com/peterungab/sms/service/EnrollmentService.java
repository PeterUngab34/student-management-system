package com.peterungab.sms.service;

import com.peterungab.sms.dao.CourseDao;
import com.peterungab.sms.dao.EnrollmentDao;
import com.peterungab.sms.dao.StudentDao;
import com.peterungab.sms.model.Course;
import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.EnrollmentFilter;
import com.peterungab.sms.model.EnrollmentStatus;
import com.peterungab.sms.model.GradeScale;
import com.peterungab.sms.model.Semester;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Enrollment and grading rules, plus GPA computation. */
public final class EnrollmentService {

    /** e.g. 2025-2026 (the second year must follow the first). */
    public static final Pattern SCHOOL_YEAR = Pattern.compile("^(\\d{4})-(\\d{4})$");

    private final EnrollmentDao enrollmentDao;
    private final StudentDao studentDao;
    private final CourseDao courseDao;
    private final Clock clock;

    public EnrollmentService(EnrollmentDao enrollmentDao, StudentDao studentDao, CourseDao courseDao, Clock clock) {
        this.enrollmentDao = enrollmentDao;
        this.studentDao = studentDao;
        this.courseDao = courseDao;
        this.clock = clock;
    }

    public List<Enrollment> search(EnrollmentFilter filter) {
        return enrollmentDao.search(filter == null ? EnrollmentFilter.all() : filter);
    }

    public Optional<Enrollment> findById(int id) {
        return enrollmentDao.findById(id);
    }

    public int count() {
        return enrollmentDao.count();
    }

    /**
     * Enrolls a student in a course for a term.
     *
     * @throws ValidationException if the student is not active, the term is malformed, or the
     *                             student is already enrolled in that course for that term
     */
    public Enrollment enroll(int studentId, int courseId, String schoolYear, Semester semester) {
        ValidationException.Errors errors = new ValidationException.Errors();
        Optional<Student> student = studentDao.findById(studentId);
        Optional<Course> course = courseDao.findById(courseId);
        String sy = schoolYear == null ? "" : schoolYear.trim();

        if (student.isEmpty()) {
            errors.add("student", "Select a student.");
        } else if (student.get().status() != StudentStatus.ACTIVE) {
            errors.add("student", "Only active students can be enrolled (" + student.get().fullName()
                    + " is " + student.get().status().label().toLowerCase() + ").");
        }
        if (course.isEmpty()) {
            errors.add("course", "Select a course.");
        }
        if (!isValidSchoolYear(sy)) {
            errors.add("schoolYear", "Use the format YYYY-YYYY, e.g. " + currentSchoolYear(LocalDate.now(clock)) + ".");
        }
        if (semester == null) {
            errors.add("semester", "Select a semester.");
        }
        if (!errors.has("student") && !errors.has("course") && !errors.has("schoolYear") && semester != null
                && enrollmentDao.exists(studentId, courseId, sy, semester)) {
            errors.add("course", student.get().fullName() + " is already enrolled in " + course.get().code()
                    + " for " + semester.shortLabel() + " " + sy + ".");
        }
        errors.throwIfAny();

        int id = enrollmentDao.insert(studentId, courseId, sy, semester);
        return enrollmentDao.findById(id).orElseThrow();
    }

    /**
     * Records (or clears, when {@code grade} is null) the final grade of an enrollment.
     * A recorded grade marks the enrollment COMPLETED; clearing it returns it to ENROLLED.
     */
    public Enrollment recordGrade(int enrollmentId, BigDecimal grade) {
        Enrollment e = enrollmentDao.findById(enrollmentId)
                .orElseThrow(() -> new BusinessRuleException("Enrollment no longer exists."));
        if (e.status() == EnrollmentStatus.DROPPED) {
            throw new BusinessRuleException("Cannot grade a dropped course.");
        }
        if (grade == null) {
            enrollmentDao.updateGrade(enrollmentId, null, EnrollmentStatus.ENROLLED);
        } else {
            if (!GradeScale.isValid(grade)) {
                throw new ValidationException("grade",
                        "Grade must be 1.00 to 3.00 in steps of 0.25, or 5.00 (failed).");
            }
            enrollmentDao.updateGrade(enrollmentId, grade.setScale(2, RoundingMode.HALF_UP), EnrollmentStatus.COMPLETED);
        }
        return enrollmentDao.findById(enrollmentId).orElseThrow();
    }

    /** Marks an in-progress enrollment as dropped. Graded courses cannot be dropped. */
    public Enrollment drop(int enrollmentId) {
        Enrollment e = enrollmentDao.findById(enrollmentId)
                .orElseThrow(() -> new BusinessRuleException("Enrollment no longer exists."));
        if (e.status() == EnrollmentStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot drop " + e.courseCode() + ": it already has a final grade.");
        }
        enrollmentDao.updateGrade(enrollmentId, null, EnrollmentStatus.DROPPED);
        return enrollmentDao.findById(enrollmentId).orElseThrow();
    }

    public void delete(int enrollmentId) {
        if (!enrollmentDao.delete(enrollmentId)) {
            throw new BusinessRuleException("Enrollment no longer exists.");
        }
    }

    /** Full transcript of a student with cumulative and per-term GPA. */
    public AcademicRecord academicRecord(int studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new BusinessRuleException("Student no longer exists."));
        List<Enrollment> enrollments = enrollmentDao.findByStudent(studentId);

        Map<String, List<Enrollment>> byTerm = new TreeMap<>();
        for (Enrollment e : enrollments) {
            byTerm.computeIfAbsent(e.termKey(), k -> new ArrayList<>()).add(e);
        }
        List<AcademicRecord.TermSummary> terms = new ArrayList<>();
        for (List<Enrollment> termEnrollments : byTerm.values()) {
            List<Enrollment> counted = termEnrollments.stream()
                    .filter(e -> e.status() != EnrollmentStatus.DROPPED).toList();
            terms.add(new AcademicRecord.TermSummary(
                    termEnrollments.get(0).term(),
                    counted.size(),
                    counted.stream().mapToInt(Enrollment::units).sum(),
                    GradeCalculator.gpa(termEnrollments)));
        }
        return new AcademicRecord(student, enrollments, GradeCalculator.gpa(enrollments),
                GradeCalculator.unitsEarned(enrollments), terms);
    }

    /** Cumulative GPA of every student that has at least one graded course, keyed by student id. */
    public Map<Integer, BigDecimal> gpaByStudent() {
        Map<Integer, List<Enrollment>> graded = enrollmentDao.findGraded().stream()
                .collect(Collectors.groupingBy(Enrollment::studentId));
        Map<Integer, BigDecimal> result = new HashMap<>();
        graded.forEach((id, list) -> GradeCalculator.gpa(list).ifPresent(gpa -> result.put(id, gpa)));
        return result;
    }

    /** School years with enrollments plus the current one, newest first. */
    public List<String> schoolYears() {
        List<String> years = new ArrayList<>(enrollmentDao.findSchoolYears());
        String current = currentSchoolYear();
        if (!years.contains(current)) {
            years.add(current);
        }
        years.sort(Comparator.reverseOrder());
        return years;
    }

    public String currentSchoolYear() {
        return currentSchoolYear(LocalDate.now(clock));
    }

    public Semester currentSemester() {
        return currentSemester(LocalDate.now(clock));
    }

    /** Philippine school years start in August: Aug 2026 - Jul 2027 is "2026-2027". */
    public static String currentSchoolYear(LocalDate date) {
        int start = date.getMonthValue() >= 8 ? date.getYear() : date.getYear() - 1;
        return start + "-" + (start + 1);
    }

    /** Aug-Dec = 1st semester, Jan-May = 2nd semester, Jun-Jul = summer (midyear) term. */
    public static Semester currentSemester(LocalDate date) {
        int m = date.getMonthValue();
        if (m >= 8) {
            return Semester.FIRST;
        }
        return m <= 5 ? Semester.SECOND : Semester.SUMMER;
    }

    public static boolean isValidSchoolYear(String schoolYear) {
        if (schoolYear == null) {
            return false;
        }
        Matcher m = SCHOOL_YEAR.matcher(schoolYear.trim());
        if (!m.matches()) {
            return false;
        }
        int start = Integer.parseInt(m.group(1));
        int end = Integer.parseInt(m.group(2));
        return end == start + 1 && start >= 1900 && start <= 2100;
    }
}
