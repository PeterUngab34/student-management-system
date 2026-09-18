package com.peterungab.sms;

import com.peterungab.sms.dao.CourseDao;
import com.peterungab.sms.dao.EnrollmentDao;
import com.peterungab.sms.dao.ProgramDao;
import com.peterungab.sms.dao.StudentDao;
import com.peterungab.sms.dao.jdbc.JdbcCourseDao;
import com.peterungab.sms.dao.jdbc.JdbcEnrollmentDao;
import com.peterungab.sms.dao.jdbc.JdbcProgramDao;
import com.peterungab.sms.dao.jdbc.JdbcStudentDao;
import com.peterungab.sms.db.Database;
import com.peterungab.sms.service.CourseService;
import com.peterungab.sms.service.DashboardService;
import com.peterungab.sms.service.EnrollmentService;
import com.peterungab.sms.service.StudentService;

import java.time.Clock;

/**
 * Composition root: wires the DAOs and services together (manual dependency injection).
 */
public record AppContext(
        Database database,
        StudentService students,
        CourseService courses,
        EnrollmentService enrollments,
        DashboardService dashboard) {

    public static AppContext create(Database db) {
        return create(db, Clock.systemDefaultZone());
    }

    public static AppContext create(Database db, Clock clock) {
        StudentDao studentDao = new JdbcStudentDao(db);
        ProgramDao programDao = new JdbcProgramDao(db);
        CourseDao courseDao = new JdbcCourseDao(db);
        EnrollmentDao enrollmentDao = new JdbcEnrollmentDao(db);

        StudentService studentService = new StudentService(studentDao, programDao, clock);
        CourseService courseService = new CourseService(courseDao, enrollmentDao);
        EnrollmentService enrollmentService = new EnrollmentService(enrollmentDao, studentDao, courseDao, clock);
        DashboardService dashboardService = new DashboardService(studentDao, courseDao, enrollmentDao, enrollmentService);

        return new AppContext(db, studentService, courseService, enrollmentService, dashboardService);
    }
}
