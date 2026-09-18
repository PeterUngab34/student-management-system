package com.peterungab.sms.dao;

import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.EnrollmentFilter;
import com.peterungab.sms.model.EnrollmentStatus;
import com.peterungab.sms.model.Semester;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface EnrollmentDao {

    List<Enrollment> search(EnrollmentFilter filter);

    List<Enrollment> findByStudent(int studentId);

    /** Every graded (COMPLETED) enrollment - the input for GPA computation. */
    List<Enrollment> findGraded();

    Optional<Enrollment> findById(int id);

    boolean exists(int studentId, int courseId, String schoolYear, Semester semester);

    /** Inserts a new ENROLLED row and returns its generated id. */
    int insert(int studentId, int courseId, String schoolYear, Semester semester);

    boolean updateGrade(int enrollmentId, BigDecimal grade, EnrollmentStatus status);

    boolean delete(int enrollmentId);

    int count();

    int countByCourse(int courseId);

    /** Distinct school years that have enrollments, newest first. */
    List<String> findSchoolYears();
}
