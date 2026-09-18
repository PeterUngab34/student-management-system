package com.peterungab.sms.dao.jdbc;

import com.peterungab.sms.dao.EnrollmentDao;
import com.peterungab.sms.db.Database;
import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.EnrollmentFilter;
import com.peterungab.sms.model.EnrollmentStatus;
import com.peterungab.sms.model.Semester;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcEnrollmentDao implements EnrollmentDao {

    private static final String SELECT = """
            SELECT e.enrollment_id, e.student_id, s.student_number, s.first_name, s.last_name,
                   e.course_id, c.course_code, c.title, c.units,
                   e.school_year, e.semester, e.grade, e.status
            FROM enrollments e
            JOIN students s ON s.student_id = e.student_id
            JOIN courses c ON c.course_id = e.course_id
            """;

    /** Newest term first; semesters in academic order (1st, 2nd, Summer). */
    private static final String ORDER_BY = """
             ORDER BY e.school_year DESC,
                      CASE e.semester WHEN 'FIRST' THEN 1 WHEN 'SECOND' THEN 2 ELSE 3 END DESC,
                      s.last_name, s.first_name, c.course_code
            """;

    private final JdbcTemplate jdbc;

    public JdbcEnrollmentDao(Database db) {
        this.jdbc = new JdbcTemplate(db);
    }

    @Override
    public List<Enrollment> search(EnrollmentFilter filter) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (JdbcTemplate.hasText(filter.keyword())) {
            String like = JdbcTemplate.likePattern(filter.keyword());
            sql.append("""
                     AND (LOWER(s.student_number) LIKE ? ESCAPE '!'
                          OR LOWER(CONCAT(s.first_name, ' ', s.last_name)) LIKE ? ESCAPE '!'
                          OR LOWER(c.course_code) LIKE ? ESCAPE '!'
                          OR LOWER(c.title) LIKE ? ESCAPE '!')
                    """);
            for (int i = 0; i < 4; i++) {
                params.add(like);
            }
        }
        if (filter.courseId() != null) {
            sql.append(" AND e.course_id = ?");
            params.add(filter.courseId());
        }
        if (JdbcTemplate.hasText(filter.schoolYear())) {
            sql.append(" AND e.school_year = ?");
            params.add(filter.schoolYear());
        }
        if (filter.semester() != null) {
            sql.append(" AND e.semester = ?");
            params.add(filter.semester());
        }
        sql.append(ORDER_BY);
        return jdbc.query(sql.toString(), JdbcEnrollmentDao::map, params.toArray());
    }

    @Override
    public List<Enrollment> findByStudent(int studentId) {
        return jdbc.query(SELECT + " WHERE e.student_id = ?" + ORDER_BY, JdbcEnrollmentDao::map, studentId);
    }

    @Override
    public List<Enrollment> findGraded() {
        return jdbc.query(SELECT + " WHERE e.status = 'COMPLETED'" + ORDER_BY, JdbcEnrollmentDao::map);
    }

    @Override
    public Optional<Enrollment> findById(int id) {
        return jdbc.queryOne(SELECT + " WHERE e.enrollment_id = ?", JdbcEnrollmentDao::map, id);
    }

    @Override
    public boolean exists(int studentId, int courseId, String schoolYear, Semester semester) {
        return jdbc.queryInt("""
                SELECT COUNT(*) FROM enrollments
                WHERE student_id = ? AND course_id = ? AND school_year = ? AND semester = ?
                """, studentId, courseId, schoolYear, semester) > 0;
    }

    @Override
    public int insert(int studentId, int courseId, String schoolYear, Semester semester) {
        return jdbc.insert("""
                INSERT INTO enrollments (student_id, course_id, school_year, semester, status)
                VALUES (?, ?, ?, ?, 'ENROLLED')
                """, studentId, courseId, schoolYear, semester);
    }

    @Override
    public boolean updateGrade(int enrollmentId, BigDecimal grade, EnrollmentStatus status) {
        return jdbc.update("UPDATE enrollments SET grade = ?, status = ? WHERE enrollment_id = ?",
                grade, status, enrollmentId) == 1;
    }

    @Override
    public boolean delete(int enrollmentId) {
        return jdbc.update("DELETE FROM enrollments WHERE enrollment_id = ?", enrollmentId) == 1;
    }

    @Override
    public int count() {
        return jdbc.queryInt("SELECT COUNT(*) FROM enrollments");
    }

    @Override
    public int countByCourse(int courseId) {
        return jdbc.queryInt("SELECT COUNT(*) FROM enrollments WHERE course_id = ?", courseId);
    }

    @Override
    public List<String> findSchoolYears() {
        return jdbc.query("SELECT DISTINCT school_year FROM enrollments ORDER BY school_year DESC",
                rs -> rs.getString(1));
    }

    private static Enrollment map(ResultSet rs) throws SQLException {
        return new Enrollment(
                rs.getInt("enrollment_id"),
                rs.getInt("student_id"),
                rs.getString("student_number"),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                rs.getInt("course_id"),
                rs.getString("course_code"),
                rs.getString("title"),
                rs.getInt("units"),
                rs.getString("school_year"),
                Semester.valueOf(rs.getString("semester")),
                rs.getBigDecimal("grade"),
                EnrollmentStatus.valueOf(rs.getString("status")));
    }
}
