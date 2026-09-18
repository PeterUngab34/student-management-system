-- =============================================================================
--  Student Management System - database schema (SQLite edition for the web demo)
--  Translated from ../../sql/schema.sql (MySQL 8). Same tables, columns, primary
--  keys, UNIQUE, FOREIGN KEY and CHECK constraints, defaults and indexes.
--
--  MySQL -> SQLite differences (everything else is identical):
--   1. `INT NOT NULL AUTO_INCREMENT` + `CONSTRAINT pk_x PRIMARY KEY (id)` becomes
--      `INTEGER PRIMARY KEY AUTOINCREMENT` declared on the column. SQLite only
--      auto-numbers a column declared exactly like that; AUTOINCREMENT keeps ids
--      from being reused after a delete, like InnoDB does.
--   2. `TINYINT` becomes `INTEGER` (SQLite has one integer type). The value
--      ranges are still enforced by the CHECK constraints below.
--   3. `VARCHAR(n)` is kept as written but SQLite does not enforce the length;
--      the service layer enforces the same maximum lengths as the desktop app.
--   4. `DECIMAL(3, 2)` becomes `NUMERIC(3, 2)`: SQLite stores 2.75 as a REAL.
--      The application formats grades with two decimals and does GPA math in
--      exact hundredths, so results match the BigDecimal arithmetic in Java.
--   5. `DATE` / `TIMESTAMP` are stored as ISO-8601 text ('YYYY-MM-DD',
--      'YYYY-MM-DD HH:MM:SS'). `DEFAULT CURRENT_TIMESTAMP` is UTC in SQLite.
--   6. Foreign keys (including ON DELETE CASCADE / RESTRICT) are only enforced
--      when the connection runs `PRAGMA foreign_keys = ON;` - the application
--      does this right after opening the database.
--   7. MySQL's `CHECK (... BETWEEN 1.00 AND 5.00)` and the status/semester
--      `IN (...)` checks are supported unchanged by SQLite 3.25+ (sql.js
--      bundles a much newer SQLite).
-- =============================================================================

DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS students;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS programs;

-- Degree programs offered by the university (e.g. BS Computer Engineering).
CREATE TABLE programs (
    program_id  INTEGER      PRIMARY KEY AUTOINCREMENT,
    code        VARCHAR(12)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    department  VARCHAR(100) NOT NULL,
    CONSTRAINT uq_programs_code UNIQUE (code)
);

-- One row per student. student_number follows the YYYY-NNNNN format (e.g. 2023-00123).
CREATE TABLE students (
    student_id      INTEGER      PRIMARY KEY AUTOINCREMENT,
    student_number  VARCHAR(10)  NOT NULL,
    first_name      VARCHAR(50)  NOT NULL,
    last_name       VARCHAR(50)  NOT NULL,
    email           VARCHAR(100) NOT NULL,
    phone           VARCHAR(13)  NULL,
    birth_date      DATE         NULL,
    program_id      INTEGER      NOT NULL,
    year_level      INTEGER      NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
    course_id    INTEGER      PRIMARY KEY AUTOINCREMENT,
    course_code  VARCHAR(12)  NOT NULL,
    title        VARCHAR(100) NOT NULL,
    units        INTEGER      NOT NULL,
    description  VARCHAR(255) NULL,
    CONSTRAINT uq_courses_code UNIQUE (course_code),
    CONSTRAINT ck_courses_units CHECK (units BETWEEN 1 AND 6)
);

-- Junction table between students and courses for a given term, carrying the grade.
-- A student can take the same course only once per term (uq_enrollment).
CREATE TABLE enrollments (
    enrollment_id  INTEGER       PRIMARY KEY AUTOINCREMENT,
    student_id     INTEGER       NOT NULL,
    course_id      INTEGER       NOT NULL,
    school_year    VARCHAR(9)    NOT NULL,
    semester       VARCHAR(6)    NOT NULL,
    grade          NUMERIC(3, 2) NULL,
    status         VARCHAR(10)   NOT NULL DEFAULT 'ENROLLED',
    enrolled_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
