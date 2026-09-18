package com.peterungab.sms.dao.jdbc;

import com.peterungab.sms.dao.CourseDao;
import com.peterungab.sms.db.Database;
import com.peterungab.sms.model.Course;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JdbcCourseDao implements CourseDao {

    private static final String SELECT = "SELECT course_id, course_code, title, units, description FROM courses";

    private final JdbcTemplate jdbc;

    public JdbcCourseDao(Database db) {
        this.jdbc = new JdbcTemplate(db);
    }

    @Override
    public List<Course> search(String keyword) {
        if (!JdbcTemplate.hasText(keyword)) {
            return jdbc.query(SELECT + " ORDER BY course_code", JdbcCourseDao::map);
        }
        String like = JdbcTemplate.likePattern(keyword);
        return jdbc.query(SELECT + """
                 WHERE LOWER(course_code) LIKE ? ESCAPE '!' OR LOWER(title) LIKE ? ESCAPE '!'
                 ORDER BY course_code
                """, JdbcCourseDao::map, like, like);
    }

    @Override
    public Optional<Course> findById(int id) {
        return jdbc.queryOne(SELECT + " WHERE course_id = ?", JdbcCourseDao::map, id);
    }

    @Override
    public Optional<Course> findByCode(String code) {
        return jdbc.queryOne(SELECT + " WHERE UPPER(course_code) = UPPER(?)", JdbcCourseDao::map, code);
    }

    @Override
    public Course insert(Course c) {
        int id = jdbc.insert("INSERT INTO courses (course_code, title, units, description) VALUES (?, ?, ?, ?)",
                c.code(), c.title(), c.units(), c.description());
        return c.withId(id);
    }

    @Override
    public boolean update(Course c) {
        return jdbc.update("UPDATE courses SET course_code = ?, title = ?, units = ?, description = ? WHERE course_id = ?",
                c.code(), c.title(), c.units(), c.description(), c.id()) == 1;
    }

    @Override
    public boolean delete(int id) {
        return jdbc.update("DELETE FROM courses WHERE course_id = ?", id) == 1;
    }

    @Override
    public int count() {
        return jdbc.queryInt("SELECT COUNT(*) FROM courses");
    }

    @Override
    public Map<Integer, Integer> countEnrollmentsByCourse() {
        Map<Integer, Integer> counts = new HashMap<>();
        jdbc.query("SELECT course_id, COUNT(*) FROM enrollments GROUP BY course_id",
                        rs -> Map.entry(rs.getInt(1), rs.getInt(2)))
                .forEach(e -> counts.put(e.getKey(), e.getValue()));
        return counts;
    }

    private static Course map(ResultSet rs) throws SQLException {
        return new Course(rs.getInt("course_id"), rs.getString("course_code"), rs.getString("title"),
                rs.getInt("units"), rs.getString("description"));
    }
}
