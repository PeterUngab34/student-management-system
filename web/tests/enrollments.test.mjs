import { test } from 'node:test';
import assert from 'node:assert/strict';
import { context, student } from './helper.mjs';
import { ValidationError, BusinessRuleError } from '../js/domain/errors.js';
import { isValidSchoolYear, currentSchoolYear, currentSemester } from '../js/domain/enrollments.js';
import { formatGrade } from '../js/domain/grades.js';

/** Empty schema so every GPA figure is fully controlled by the test (like the Java test). */
async function fixture() {
  const ctx = await context(false);
  const s = ctx.students.create(student());
  const oop = ctx.courses.create({ code: 'CS 211', title: 'Object-Oriented Programming', units: 3 });
  const dsa = ctx.courses.create({ code: 'CPE 201', title: 'Data Structures and Algorithms', units: 3 });
  const circuits = ctx.courses.create({ code: 'ECE 211', title: 'Electronic Circuits', units: 4 });
  const grade = (course, sy, sem, g) => ctx.enrollments.recordGrade(ctx.enrollments.enroll(s.id, course.id, sy, sem).id, g);
  return { ctx, s, oop, dsa, circuits, grade };
}

test('enrolls an active student', async () => {
  const { ctx, s, oop } = await fixture();
  const e = ctx.enrollments.enroll(s.id, oop.id, '2026-2027', 'FIRST');
  assert.equal(e.status, 'ENROLLED');
  assert.equal(e.grade, null);
  assert.equal(e.courseCode, 'CS 211');
  assert.equal(e.studentName, 'Ana Reyes');
  assert.equal(e.term, '1st Sem 2026-2027');
});

test('rejects a duplicate enrollment in the same term, allows a retake in another term', async () => {
  const { ctx, s, oop } = await fixture();
  ctx.enrollments.enroll(s.id, oop.id, '2026-2027', 'FIRST');
  assert.throws(() => ctx.enrollments.enroll(s.id, oop.id, '2026-2027', 'FIRST'), (e) =>
    e instanceof ValidationError && e.errors.course === 'Ana Reyes is already enrolled in CS 211 for 1st Sem 2026-2027.');
  assert.equal(ctx.enrollments.count(), 1);
  ctx.enrollments.enroll(s.id, oop.id, '2026-2027', 'SECOND');
  assert.equal(ctx.enrollments.count(), 2);
});

test('only active students can be enrolled', async () => {
  const { ctx, s, oop } = await fixture();
  ctx.students.update({ ...s, programId: s.program.id, status: 'GRADUATED' });
  assert.throws(() => ctx.enrollments.enroll(s.id, oop.id, '2026-2027', 'FIRST'), (e) =>
    e instanceof ValidationError && e.errors.student === 'Only active students can be enrolled (Ana Reyes is graduated).');
  assert.throws(() => ctx.enrollments.enroll(null, oop.id, '2026-2027', 'FIRST'), (e) => e.errors.student === 'Select a student.');
  assert.throws(() => ctx.enrollments.enroll(s.id, 999, '2026-2027', 'FIRST'), (e) => e.errors.course === 'Select a course.');
});

test('validates the school year and semester', async () => {
  const { ctx, s, oop } = await fixture();
  assert.ok(isValidSchoolYear('2025-2026'));
  assert.ok(!isValidSchoolYear('2025-2027'));
  assert.ok(!isValidSchoolYear('25-26'));
  assert.throws(() => ctx.enrollments.enroll(s.id, oop.id, '2026', 'FIRST'), (e) =>
    e.errors.schoolYear === 'Use the format YYYY-YYYY, e.g. 2026-2027.');
  assert.throws(() => ctx.enrollments.enroll(s.id, oop.id, '2026-2027', 'THIRD'), (e) => e.errors.semester === 'Select a semester.');
});

test('recording a grade completes the enrollment, clearing it reopens it, the scale is enforced', async () => {
  const { ctx, s, oop } = await fixture();
  const e = ctx.enrollments.enroll(s.id, oop.id, '2026-2027', 'FIRST');
  const graded = ctx.enrollments.recordGrade(e.id, 150);
  assert.equal(graded.status, 'COMPLETED');
  assert.equal(formatGrade(graded.grade), '1.50');
  const cleared = ctx.enrollments.recordGrade(e.id, null);
  assert.equal(cleared.status, 'ENROLLED');
  assert.equal(cleared.grade, null);
  for (const bad of [75, 110, 325, 400, 525]) {
    assert.throws(() => ctx.enrollments.recordGrade(e.id, bad), (err) =>
      err instanceof ValidationError && err.errors.grade === 'Grade must be 1.00 to 3.00 in steps of 0.25, or 5.00 (failed).');
  }
});

test('drop rules: dropped courses cannot be graded, graded courses cannot be dropped', async () => {
  const { ctx, s, oop, dsa } = await fixture();
  const e = ctx.enrollments.enroll(s.id, oop.id, '2026-2027', 'FIRST');
  assert.equal(ctx.enrollments.drop(e.id).status, 'DROPPED');
  assert.throws(() => ctx.enrollments.recordGrade(e.id, 200), (err) => err instanceof BusinessRuleError && err.message === 'Cannot grade a dropped course.');
  const graded = ctx.enrollments.enroll(s.id, dsa.id, '2026-2027', 'FIRST');
  ctx.enrollments.recordGrade(graded.id, 200);
  assert.throws(() => ctx.enrollments.drop(graded.id), (err) => err.message === 'Cannot drop CPE 201: it already has a final grade.');
  ctx.enrollments.delete(graded.id);
  assert.throws(() => ctx.enrollments.delete(graded.id), BusinessRuleError);
});

test('academic record: unit-weighted cumulative and per-term GPA from the database', async () => {
  const { ctx, s, oop, dsa, circuits, grade } = await fixture();
  // 3 x 1.25 + 3 x 2.00 + 4 x 1.75 = 16.75 / 10 = 1.675 -> 1.68
  grade(oop, '2025-2026', 'FIRST', 125);
  grade(dsa, '2025-2026', 'FIRST', 200);
  grade(circuits, '2025-2026', 'SECOND', 175);
  ctx.enrollments.enroll(s.id, oop.id, '2026-2027', 'FIRST'); // retake, in progress: ignored

  const record = ctx.enrollments.academicRecord(s.id);
  assert.equal(formatGrade(record.gpa), '1.68');
  assert.equal(record.unitsEarned, 10);
  assert.equal(record.enrollments.length, 4);
  assert.deepEqual(record.terms.map((t) => t.term), ['1st Sem 2025-2026', '2nd Sem 2025-2026', '1st Sem 2026-2027']);
  assert.equal(formatGrade(record.terms[0].gpa), '1.63'); // (3.75 + 6) / 6 = 1.625
  assert.equal(record.terms[0].courses, 2);
  assert.equal(record.terms[0].units, 6);
  assert.equal(record.terms[2].gpa, null);
  assert.equal(ctx.enrollments.gpaByStudent().get(s.id), 168);
});

test('failed courses count toward the GPA but not toward units earned', async () => {
  const { ctx, s, oop, dsa, grade } = await fixture();
  grade(oop, '2025-2026', 'FIRST', 500);
  grade(dsa, '2025-2026', 'FIRST', 100);
  const record = ctx.enrollments.academicRecord(s.id);
  assert.equal(formatGrade(record.gpa), '3.00');
  assert.equal(record.unitsEarned, 3);
});

test('filters enrollments by term, course and keyword; school years include the current one', async () => {
  const { ctx, s, oop, dsa, circuits, grade } = await fixture();
  grade(oop, '2025-2026', 'FIRST', 125);
  ctx.enrollments.enroll(s.id, dsa.id, '2026-2027', 'FIRST');
  ctx.enrollments.enroll(s.id, circuits.id, '2026-2027', 'FIRST');
  assert.equal(ctx.enrollments.search({ schoolYear: '2026-2027', semester: 'FIRST' }).length, 2);
  assert.equal(ctx.enrollments.search({ courseId: oop.id }).length, 1);
  assert.equal(ctx.enrollments.search({ keyword: 'circuits' }).length, 1);
  assert.equal(ctx.enrollments.search({ keyword: 'ana reyes' }).length, 3);
  assert.deepEqual(ctx.enrollments.schoolYears(), ['2026-2027', '2025-2026']);
});

test('derives the current term from the date', () => {
  assert.equal(currentSchoolYear({ year: 2026, month: 9 }), '2026-2027');
  assert.equal(currentSemester({ month: 9 }), 'FIRST');
  assert.equal(currentSchoolYear({ year: 2026, month: 2 }), '2025-2026');
  assert.equal(currentSemester({ month: 2 }), 'SECOND');
  assert.equal(currentSemester({ month: 6 }), 'SUMMER');
});

test('seeded database: GPAs, Dean\'s List and dashboard figures match the desktop screenshots', async () => {
  const ctx = await context(true);
  const gpas = ctx.enrollments.gpaByStudent();
  assert.ok(gpas.size >= 20);
  for (const s of ctx.students.findAll()) {
    assert.equal(ctx.enrollments.academicRecord(s.id).gpa, gpas.get(s.id) ?? null, s.lastName);
  }
  const byNumber = (n) => formatGrade(gpas.get(ctx.students.findByStudentNumber(n).id));
  assert.equal(byNumber('2025-00312'), '1.00'); // Kristine Ramos
  assert.equal(byNumber('2023-00171'), '1.39'); // John Paul Navarro
  assert.equal(byNumber('2024-00230'), '1.50'); // Joshua Aquino
  assert.equal(byNumber('2024-00277'), '1.58'); // Hazel Lim
  // Maria Santos: (5.00x3 + 2.75x3 + 1.75x3 + 3.00x3 + 2.50x4 + 2.00x3 + 3.00x3 + 2.00x3) / 25 = 68.5 / 25 = 2.74
  assert.equal(byNumber('2023-00123'), '2.74');

  const stats = ctx.dashboard.load();
  assert.equal(stats.totalStudents, 25);
  assert.equal(stats.activeStudents, 22);
  assert.equal(stats.totalCourses, 10);
  assert.equal(stats.totalEnrollments, 153);
  assert.equal(stats.currentTerm, '1st Sem 2026-2027');
  assert.equal(stats.currentTermEnrollments, 32);
  assert.equal(formatGrade(stats.averageGpa), '2.01');
  assert.equal(stats.deansListCount, 5);
  assert.deepEqual(stats.gradeDistribution.map(([, n]) => n), [6, 9, 13, 15, 23, 18, 14, 12, 5, 1]);
  assert.deepEqual(stats.studentsByProgram, [['BSCpE', 10], ['BSCS', 6], ['BSIT', 5], ['BSECE', 4]]);
  assert.deepEqual(stats.currentTermCourseLoad.slice(0, 3), [['CPE 201', 6], ['IT 231', 6], ['CS 211', 5]]);
  assert.deepEqual(stats.topStudents.map((t) => t.student.lastName), ['Ramos', 'Navarro', 'Aquino', 'Lim', 'Pascual']);
});
