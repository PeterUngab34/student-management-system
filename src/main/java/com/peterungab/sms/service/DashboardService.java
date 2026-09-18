package com.peterungab.sms.service;

import com.peterungab.sms.dao.CourseDao;
import com.peterungab.sms.dao.EnrollmentDao;
import com.peterungab.sms.dao.StudentDao;
import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.EnrollmentFilter;
import com.peterungab.sms.model.EnrollmentStatus;
import com.peterungab.sms.model.GradeScale;
import com.peterungab.sms.model.Semester;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentFilter;
import com.peterungab.sms.model.StudentStatus;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Aggregates the numbers shown on the dashboard. */
public final class DashboardService {

    /** GPA at or better than this makes a student a Dean's List candidate. */
    public static final BigDecimal DEANS_LIST_GPA = new BigDecimal("1.75");

    private final StudentDao studentDao;
    private final CourseDao courseDao;
    private final EnrollmentDao enrollmentDao;
    private final EnrollmentService enrollmentService;

    public DashboardService(StudentDao studentDao, CourseDao courseDao, EnrollmentDao enrollmentDao,
                            EnrollmentService enrollmentService) {
        this.studentDao = studentDao;
        this.courseDao = courseDao;
        this.enrollmentDao = enrollmentDao;
        this.enrollmentService = enrollmentService;
    }

    public record RankedStudent(Student student, BigDecimal gpa) {
        String sortableName() {
            return student.sortableName();
        }
    }

    public record Stats(
            int totalStudents,
            int activeStudents,
            int totalCourses,
            int totalEnrollments,
            int currentTermEnrollments,
            String currentTerm,
            Optional<BigDecimal> averageGpa,
            int deansListCount,
            Map<String, Integer> gradeDistribution,
            Map<String, Integer> studentsByProgram,
            Map<String, Integer> currentTermCourseLoad,
            List<RankedStudent> topStudents) {
    }

    public Stats load() {
        List<Student> students = studentDao.search(StudentFilter.all());
        Map<Integer, Student> byId = students.stream().collect(Collectors.toMap(Student::id, Function.identity()));
        Map<Integer, BigDecimal> gpas = enrollmentService.gpaByStudent();

        // grade distribution across all graded enrollments, one bucket per valid grade
        Map<String, Integer> distribution = new LinkedHashMap<>();
        GradeScale.VALID_GRADES.forEach(g -> distribution.put(GradeScale.format(g), 0));
        for (Enrollment e : enrollmentDao.findGraded()) {
            distribution.merge(GradeScale.format(e.grade()), 1, Integer::sum);
        }

        String schoolYear = enrollmentService.currentSchoolYear();
        Semester semester = enrollmentService.currentSemester();
        List<Enrollment> current = enrollmentDao.search(new EnrollmentFilter(null, null, schoolYear, semester))
                .stream().filter(e -> e.status() != EnrollmentStatus.DROPPED).toList();
        Map<String, Integer> courseLoad = current.stream()
                .collect(Collectors.groupingBy(Enrollment::courseCode, Collectors.summingInt(e -> 1)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        List<RankedStudent> top = gpas.entrySet().stream()
                .filter(e -> byId.containsKey(e.getKey()))
                .map(e -> new RankedStudent(byId.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(RankedStudent::gpa).thenComparing(RankedStudent::sortableName))
                .limit(5)
                .toList();

        int deansList = (int) gpas.values().stream().filter(g -> g.compareTo(DEANS_LIST_GPA) <= 0).count();
        int active = (int) students.stream().filter(s -> s.status() == StudentStatus.ACTIVE).count();

        return new Stats(
                students.size(),
                active,
                courseDao.count(),
                enrollmentDao.count(),
                current.size(),
                semester.shortLabel() + " " + schoolYear,
                GradeCalculator.average(gpas.values()),
                deansList,
                distribution,
                studentDao.countByProgram(),
                courseLoad,
                top);
    }
}
