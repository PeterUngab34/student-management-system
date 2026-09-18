package com.peterungab.sms.service;

import com.peterungab.sms.dao.CourseDao;
import com.peterungab.sms.dao.EnrollmentDao;
import com.peterungab.sms.model.Course;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Business logic for the course catalogue. */
public final class CourseService {

    /** Subject prefix + number, e.g. "CPE 301", "MATH 201", "CS 211L". */
    public static final Pattern COURSE_CODE = Pattern.compile("^[A-Z]{2,5} \\d{2,4}[A-Z]?$");
    public static final int MIN_UNITS = 1;
    public static final int MAX_UNITS = 6;

    private final CourseDao courseDao;
    private final EnrollmentDao enrollmentDao;

    public CourseService(CourseDao courseDao, EnrollmentDao enrollmentDao) {
        this.courseDao = courseDao;
        this.enrollmentDao = enrollmentDao;
    }

    public List<Course> search(String keyword) {
        return courseDao.search(keyword);
    }

    public List<Course> findAll() {
        return courseDao.search(null);
    }

    public Optional<Course> findById(int id) {
        return courseDao.findById(id);
    }

    public int count() {
        return courseDao.count();
    }

    public Map<Integer, Integer> enrollmentCounts() {
        return courseDao.countEnrollmentsByCourse();
    }

    public Course create(Course course) {
        Course c = normalize(course);
        validate(c, null);
        return courseDao.insert(c.withId(null));
    }

    public Course update(Course course) {
        if (course.id() == null) {
            throw new IllegalArgumentException("Cannot update a course that has not been saved");
        }
        Course c = normalize(course);
        validate(c, c.id());
        if (!courseDao.update(c)) {
            throw new BusinessRuleException("Course no longer exists. It may have been deleted.");
        }
        return c;
    }

    /** Courses with enrollment history cannot be deleted - grades must never silently disappear. */
    public void delete(int courseId) {
        Course course = courseDao.findById(courseId)
                .orElseThrow(() -> new BusinessRuleException("Course no longer exists. It may have been deleted."));
        int enrollments = enrollmentDao.countByCourse(courseId);
        if (enrollments > 0) {
            throw new BusinessRuleException(course.code() + " has " + enrollments
                    + " enrollment record(s) and cannot be deleted. Remove those enrollments first.");
        }
        courseDao.delete(courseId);
    }

    /** "cpe301 " becomes "CPE 301". */
    static String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        String c = code.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        return c.replaceAll("^([A-Z]+)\\s*(\\d)", "$1 $2");
    }

    static Course normalize(Course c) {
        String title = c.title() == null ? "" : c.title().trim().replaceAll("\\s+", " ");
        String description = c.description() == null || c.description().isBlank() ? null : c.description().trim();
        return new Course(c.id(), normalizeCode(c.code()), title, c.units(), description);
    }

    private void validate(Course c, Integer existingId) {
        ValidationException.Errors errors = new ValidationException.Errors();
        if (c.code().isEmpty()) {
            errors.add("code", "Course code is required.");
        } else if (!COURSE_CODE.matcher(c.code()).matches()) {
            errors.add("code", "Use a code like CPE 301 or MATH 201.");
        } else {
            Optional<Course> existing = courseDao.findByCode(c.code());
            if (existing.isPresent() && !Objects.equals(existing.get().id(), existingId)) {
                errors.add("code", "Course code " + c.code() + " already exists.");
            }
        }
        if (c.title().isEmpty()) {
            errors.add("title", "Title is required.");
        } else if (c.title().length() > 100) {
            errors.add("title", "Title must be at most 100 characters.");
        }
        if (c.units() < MIN_UNITS || c.units() > MAX_UNITS) {
            errors.add("units", "Units must be between " + MIN_UNITS + " and " + MAX_UNITS + ".");
        }
        if (c.description() != null && c.description().length() > 255) {
            errors.add("description", "Description must be at most 255 characters.");
        }
        errors.throwIfAny();
    }
}
