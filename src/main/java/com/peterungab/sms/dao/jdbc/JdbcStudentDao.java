package com.peterungab.sms.dao.jdbc;

import com.peterungab.sms.dao.StudentDao;
import com.peterungab.sms.db.Database;
import com.peterungab.sms.model.Program;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentFilter;
import com.peterungab.sms.model.StudentStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JdbcStudentDao implements StudentDao {

    private static final String SELECT = """
            SELECT s.student_id, s.student_number, s.first_name, s.last_name, s.email, s.phone,
                   s.birth_date, s.year_level, s.status,
                   p.program_id, p.code AS program_code, p.name AS program_name, p.department
            FROM students s
            JOIN programs p ON p.program_id = s.program_id
            """;

    private static final String KEYWORD_CONDITION = """
             AND (LOWER(s.student_number) LIKE ? ESCAPE '!'
                  OR LOWER(s.first_name) LIKE ? ESCAPE '!'
                  OR LOWER(s.last_name) LIKE ? ESCAPE '!'
                  OR LOWER(CONCAT(s.first_name, ' ', s.last_name)) LIKE ? ESCAPE '!'
                  OR LOWER(s.email) LIKE ? ESCAPE '!')
            """;

    private final JdbcTemplate jdbc;

    public JdbcStudentDao(Database db) {
        this.jdbc = new JdbcTemplate(db);
    }

    @Override
    public List<Student> search(StudentFilter filter) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (JdbcTemplate.hasText(filter.keyword())) {
            String like = JdbcTemplate.likePattern(filter.keyword());
            sql.append(KEYWORD_CONDITION);
            for (int i = 0; i < 5; i++) {
                params.add(like);
            }
        }
        if (filter.programId() != null) {
            sql.append(" AND s.program_id = ?");
            params.add(filter.programId());
        }
        if (filter.yearLevel() != null) {
            sql.append(" AND s.year_level = ?");
            params.add(filter.yearLevel());
        }
        if (filter.status() != null) {
            sql.append(" AND s.status = ?");
            params.add(filter.status());
        }
        // ORDER BY comes from a whitelisted enum, never from user input
        sql.append(" ORDER BY ").append(filter.sortBy().orderBy(filter.ascending()));

        return jdbc.query(sql.toString(), JdbcStudentDao::map, params.toArray());
    }

    @Override
    public Optional<Student> findById(int id) {
        return jdbc.queryOne(SELECT + " WHERE s.student_id = ?", JdbcStudentDao::map, id);
    }

    @Override
    public Optional<Student> findByStudentNumber(String studentNumber) {
        return jdbc.queryOne(SELECT + " WHERE s.student_number = ?", JdbcStudentDao::map, studentNumber);
    }

    @Override
    public Optional<Student> findByEmail(String email) {
        return jdbc.queryOne(SELECT + " WHERE LOWER(s.email) = LOWER(?)", JdbcStudentDao::map, email);
    }

    @Override
    public Optional<String> findMaxStudentNumber(String prefix) {
        return jdbc.queryOne("SELECT MAX(student_number) FROM students WHERE student_number LIKE ?",
                rs -> rs.getString(1), prefix + "%");
    }

    @Override
    public Student insert(Student s) {
        int id = jdbc.insert("""
                INSERT INTO students (student_number, first_name, last_name, email, phone, birth_date,
                                      program_id, year_level, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                s.studentNumber(), s.firstName(), s.lastName(), s.email(), s.phone(), s.birthDate(),
                s.program().id(), s.yearLevel(), s.status());
        return s.withId(id);
    }

    @Override
    public boolean update(Student s) {
        return jdbc.update("""
                UPDATE students
                SET student_number = ?, first_name = ?, last_name = ?, email = ?, phone = ?, birth_date = ?,
                    program_id = ?, year_level = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE student_id = ?
                """,
                s.studentNumber(), s.firstName(), s.lastName(), s.email(), s.phone(), s.birthDate(),
                s.program().id(), s.yearLevel(), s.status(), s.id()) == 1;
    }

    @Override
    public boolean delete(int id) {
        return jdbc.update("DELETE FROM students WHERE student_id = ?", id) == 1;
    }

    @Override
    public int count() {
        return jdbc.queryInt("SELECT COUNT(*) FROM students");
    }

    @Override
    public Map<String, Integer> countByProgram() {
        Map<String, Integer> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT p.code, COUNT(s.student_id) AS total
                FROM programs p
                LEFT JOIN students s ON s.program_id = p.program_id
                GROUP BY p.code
                ORDER BY total DESC, p.code
                """, rs -> Map.entry(rs.getString(1), rs.getInt(2)))
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private static Student map(ResultSet rs) throws SQLException {
        Program program = new Program(rs.getInt("program_id"), rs.getString("program_code"),
                rs.getString("program_name"), rs.getString("department"));
        return new Student(
                rs.getInt("student_id"),
                rs.getString("student_number"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getObject("birth_date", LocalDate.class),
                program,
                rs.getInt("year_level"),
                StudentStatus.valueOf(rs.getString("status")));
    }
}
