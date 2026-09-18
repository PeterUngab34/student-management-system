import { test } from 'node:test';
import assert from 'node:assert/strict';
import { context } from './helper.mjs';
import { DataAccessError } from '../js/db.js';

test('schema + seed load to exactly 4 programs, 25 students, 10 courses, 153 enrollments', async () => {
  const { db } = await context(true);
  assert.equal(db.queryInt('SELECT COUNT(*) FROM programs'), 4);
  assert.equal(db.queryInt('SELECT COUNT(*) FROM students'), 25);
  assert.equal(db.queryInt('SELECT COUNT(*) FROM courses'), 10);
  assert.equal(db.queryInt('SELECT COUNT(*) FROM enrollments'), 153);
});

test('every seeded foreign key resolved through the natural-key subqueries', async () => {
  const { db } = await context(true);
  assert.equal(db.queryInt('SELECT COUNT(*) FROM students WHERE program_id IS NULL'), 0);
  assert.equal(db.queryInt('SELECT COUNT(*) FROM enrollments WHERE student_id IS NULL OR course_id IS NULL'), 0);
  assert.equal(db.queryInt('SELECT COUNT(*) FROM enrollments e LEFT JOIN students s ON s.student_id = e.student_id WHERE s.student_id IS NULL'), 0);
  assert.deepEqual(db.query('PRAGMA foreign_key_check'), []);
});

test('indexes and constraints exist with the same names as the MySQL schema', async () => {
  const { db } = await context(true);
  const names = db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'idx_%' ORDER BY name").map((r) => r.name);
  assert.deepEqual(names, ['idx_enrollments_course', 'idx_enrollments_term', 'idx_students_filter', 'idx_students_name']);
  const ddl = db.queryValue("SELECT sql FROM sqlite_master WHERE name = 'enrollments'");
  for (const c of ['uq_enrollment', 'fk_enrollments_student', 'fk_enrollments_course', 'ck_enrollments_semester',
    'ck_enrollments_grade', 'ck_enrollments_status', 'ck_enrollments_graded']) {
    assert.ok(ddl.includes(c), c);
  }
});

test('CHECK constraints reject bad grades, statuses, year levels and unit counts', async () => {
  const { db } = await context(true);
  assert.throws(() => db.run("UPDATE enrollments SET grade = 6.00, status = 'COMPLETED' WHERE enrollment_id = 1"), DataAccessError);
  assert.throws(() => db.run("UPDATE enrollments SET grade = NULL, status = 'COMPLETED' WHERE enrollment_id = 1"), DataAccessError);
  assert.throws(() => db.run("UPDATE enrollments SET status = 'PASSED' WHERE enrollment_id = 1"), DataAccessError);
  assert.throws(() => db.run('UPDATE students SET year_level = 6 WHERE student_id = 1'), DataAccessError);
  assert.throws(() => db.run("UPDATE students SET status = 'EXPELLED' WHERE student_id = 1"), DataAccessError);
  assert.throws(() => db.run('UPDATE courses SET units = 7 WHERE course_id = 1'), DataAccessError);
});

test('UNIQUE constraints: student number, e-mail, course code, one enrollment per course per term', async () => {
  const { db } = await context(true);
  assert.throws(() => db.run("UPDATE students SET student_number = '2023-00123' WHERE student_id = 2"), DataAccessError);
  assert.throws(() => db.run("UPDATE students SET email = 'maria.santos@student.example.edu.ph' WHERE student_id = 2"), DataAccessError);
  assert.throws(() => db.run("UPDATE courses SET course_code = 'CPE 201' WHERE course_id = 2"), DataAccessError);
  const e = db.queryOne('SELECT student_id, course_id, school_year, semester FROM enrollments LIMIT 1');
  assert.throws(() => db.run('INSERT INTO enrollments (student_id, course_id, school_year, semester) VALUES (?, ?, ?, ?)',
    [e.student_id, e.course_id, e.school_year, e.semester]), DataAccessError);
});

test('foreign keys are enforced: deleting a student cascades, deleting a course with history is restricted', async () => {
  const { db } = await context(true);
  const before = db.queryInt('SELECT COUNT(*) FROM enrollments WHERE student_id = 1');
  assert.ok(before > 0);
  db.run('DELETE FROM students WHERE student_id = 1');
  assert.equal(db.queryInt('SELECT COUNT(*) FROM enrollments WHERE student_id = 1'), 0);
  assert.equal(db.queryInt('SELECT COUNT(*) FROM enrollments'), 153 - before);
  assert.throws(() => db.run('DELETE FROM courses WHERE course_id = 1'), DataAccessError);
  assert.throws(() => db.run('INSERT INTO students (student_number, first_name, last_name, email, program_id, year_level) VALUES (?, ?, ?, ?, ?, ?)',
    ['2026-99999', 'X', 'Y', 'x@y.ph', 999, 1]), DataAccessError);
});

test('AUTOINCREMENT ids are never reused after a delete', async () => {
  const { db } = await context(true);
  const maxBefore = db.queryInt('SELECT MAX(student_id) FROM students');
  db.run('DELETE FROM students WHERE student_id = ?', [maxBefore]);
  const id = db.insert('INSERT INTO students (student_number, first_name, last_name, email, program_id, year_level) VALUES (?, ?, ?, ?, ?, ?)',
    ['2026-99999', 'New', 'Student', 'new.student@example.edu.ph', 1, 1]);
  assert.equal(id, maxBefore + 1);
});

test('the SQL console helper is read-only (PRAGMA query_only) and caps rows', async () => {
  const { db } = await context(true);
  const result = db.select('SELECT s.last_name, p.code FROM students s JOIN programs p ON p.program_id = s.program_id ORDER BY 1', { maxRows: 10 });
  assert.deepEqual(result.columns, ['last_name', 'code']);
  assert.equal(result.rows.length, 10);
  assert.equal(result.truncated, true);
  assert.throws(() => db.select("DELETE FROM students"), /readonly|query_only|attempt to write/i);
  assert.equal(db.queryInt('SELECT COUNT(*) FROM students'), 25);
  // writes work again after the console call
  db.run('DELETE FROM enrollments WHERE enrollment_id = 1');
  assert.equal(db.queryInt('SELECT COUNT(*) FROM enrollments'), 152);
});
