package com.peterungab.sms.dao;

import com.peterungab.sms.model.Course;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CourseDao {

    /** Courses whose code or title contains the keyword ({@code null} = all), ordered by code. */
    List<Course> search(String keyword);

    Optional<Course> findById(int id);

    Optional<Course> findByCode(String code);

    Course insert(Course course);

    boolean update(Course course);

    boolean delete(int id);

    int count();

    /** Number of enrollments (any status) per course id. */
    Map<Integer, Integer> countEnrollmentsByCourse();
}
