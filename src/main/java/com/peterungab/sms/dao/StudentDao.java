package com.peterungab.sms.dao;

import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentFilter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface StudentDao {

    /** Returns the students matching the filter, ordered as requested. */
    List<Student> search(StudentFilter filter);

    Optional<Student> findById(int id);

    Optional<Student> findByStudentNumber(String studentNumber);

    Optional<Student> findByEmail(String email);

    /** Highest student number that starts with the given prefix (e.g. "2026-"), if any. */
    Optional<String> findMaxStudentNumber(String prefix);

    /** Inserts the student and returns it with its generated id. */
    Student insert(Student student);

    /** @return {@code true} if a row was updated */
    boolean update(Student student);

    /** Deletes the student (enrollments are removed by the ON DELETE CASCADE rule). */
    boolean delete(int id);

    int count();

    /** Number of students per program code, e.g. {"BSCpE": 10}. */
    Map<String, Integer> countByProgram();
}
