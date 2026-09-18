-- =============================================================================
--  Student Management System - database schema
--  Target: MySQL 8.0.16+ (CHECK constraints enforced).
--  The same script also runs unchanged on H2 in MySQL compatibility mode,
--  which the application uses as a zero-setup embedded fallback.
--
--  Usage (MySQL):
--    CREATE DATABASE student_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--    USE student_management;
--    SOURCE sql/schema.sql;
--    SOURCE sql/seed.sql;
--
--  Grading scale (Philippine 1.00 - 5.00 system):
--    1.00 highest ... 3.00 lowest passing grade, 5.00 failed.
--    A NULL grade means the course is still in progress (or was dropped).
-- =============================================================================

DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS students;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS programs;

-- Degree programs offered by the university (e.g. BS Computer Engineering).
CREATE TABLE programs (
    program_id  INT          NOT NULL AUTO_INCREMENT,
    code        VARCHAR(12)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    department  VARCHAR(100) NOT NULL,
    CONSTRAINT pk_programs PRIMARY KEY (program_id),
    CONSTRAINT uq_programs_code UNIQUE (code)
);

-- One row per student. student_number follows the YYYY-NNNNN format (e.g. 2023-00123).
CREATE TABLE students (
    student_id      INT          NOT NULL AUTO_INCREMENT,
    student_number  VARCHAR(10)  NOT NULL,
    first_name      VARCHAR(50)  NOT NULL,
    last_name       VARCHAR(50)  NOT NULL,
    email           VARCHAR(100) NOT NULL,
    phone           VARCHAR(13)  NULL,
    birth_date      DATE         NULL,
    program_id      INT          NOT NULL,
    year_level      TINYINT      NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_students PRIMARY KEY (student_id),
    CONSTRAINT uq_students_number UNIQUE (student_number),
    CONSTRAINT uq_students_email UNIQUE (email),
    CONSTRAINT fk_students_program FOREIGN KEY (program_id)
        REFERENCES programs (program_id) ON DELETE RESTRICT,
    CONSTRAINT ck_students_year CHECK (year_level BETWEEN 1 AND 5),
    CONSTRAINT ck_students_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ON_LEAVE', 'GRADUATED'))
);

CREATE INDEX idx_students_name ON students (last_name, first_name);
CREATE INDEX idx_students_filter ON students (program_id, year_level, status);

-- Course catalogue. units = credit units used to weight the GPA.
CREATE TABLE courses (
    course_id    INT          NOT NULL AUTO_INCREMENT,
    course_code  VARCHAR(12)  NOT NULL,
    title        VARCHAR(100) NOT NULL,
    units        TINYINT      NOT NULL,
    description  VARCHAR(255) NULL,
    CONSTRAINT pk_courses PRIMARY KEY (course_id),
    CONSTRAINT uq_courses_code UNIQUE (course_code),
    CONSTRAINT ck_courses_units CHECK (units BETWEEN 1 AND 6)
);

-- Junction table between students and courses for a given term, carrying the grade.
-- A student can take the same course only once per term (uq_enrollment).
CREATE TABLE enrollments (
    enrollment_id  INT           NOT NULL AUTO_INCREMENT,
    student_id     INT           NOT NULL,
    course_id      INT           NOT NULL,
    school_year    VARCHAR(9)    NOT NULL,
    semester       VARCHAR(6)    NOT NULL,
    grade          DECIMAL(3, 2) NULL,
    status         VARCHAR(10)   NOT NULL DEFAULT 'ENROLLED',
    enrolled_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_enrollments PRIMARY KEY (enrollment_id),
    CONSTRAINT uq_enrollment UNIQUE (student_id, course_id, school_year, semester),
    CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id)
        REFERENCES students (student_id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id)
        REFERENCES courses (course_id) ON DELETE RESTRICT,
    CONSTRAINT ck_enrollments_semester CHECK (semester IN ('FIRST', 'SECOND', 'SUMMER')),
    CONSTRAINT ck_enrollments_grade CHECK (grade IS NULL OR grade BETWEEN 1.00 AND 5.00),
    CONSTRAINT ck_enrollments_status CHECK (status IN ('ENROLLED', 'COMPLETED', 'DROPPED')),
    CONSTRAINT ck_enrollments_graded CHECK (
        (status = 'COMPLETED' AND grade IS NOT NULL) OR (status <> 'COMPLETED' AND grade IS NULL))
);

CREATE INDEX idx_enrollments_course ON enrollments (course_id);
CREATE INDEX idx_enrollments_term ON enrollments (school_year, semester);
