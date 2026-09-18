import { test } from 'node:test';
import assert from 'node:assert/strict';
import { context, student } from './helper.mjs';
import { ValidationError } from '../js/domain/errors.js';
import { normalize, isIsoDate } from '../js/domain/students.js';

function errorsOf(fn) {
  try {
    fn();
  } catch (e) {
    if (e instanceof ValidationError) return e.errors;
    throw e;
  }
  assert.fail('expected a ValidationError');
}

test('creates, updates and deletes a student', async () => {
  const ctx = await context(false);
  const saved = ctx.students.create(student({ firstName: '  Ana  ', email: 'ANA.Reyes@Example.edu.ph ', phone: '0917-123 4567' }));
  assert.ok(saved.id > 0);
  assert.equal(saved.firstName, 'Ana');
  assert.equal(saved.email, 'ana.reyes@example.edu.ph');
  assert.equal(saved.phone, '09171234567');
  assert.equal(saved.program.code, 'BSCpE');

  const updated = ctx.students.update({ ...saved, programId: saved.program.id, yearLevel: 4, status: 'ON_LEAVE' });
  assert.equal(updated.yearLevel, 4);
  assert.equal(updated.status, 'ON_LEAVE');
  ctx.students.delete(saved.id);
  assert.equal(ctx.students.count(), 0);
});

test('validation messages match the Java StudentService', async () => {
  const ctx = await context(true);
  const errors = errorsOf(() => ctx.students.create({
    studentNumber: '2023-00123', firstName: 'Juan', lastName: 'Dela Cruz', email: 'juan.delacruz@',
    phone: '0917-555', birthDate: '2031-02-14', programId: 1, yearLevel: 1, status: 'ACTIVE',
  }));
  assert.deepEqual(errors, {
    studentNumber: 'Student number 2023-00123 is already in use.',
    email: 'Enter a valid e-mail address.',
    phone: 'Use a PH mobile number, e.g. 09171234567.',
    birthDate: 'Birth date cannot be in the future.',
  });

  const empty = errorsOf(() => ctx.students.create({ studentNumber: '', firstName: '', lastName: '', email: '', programId: null, yearLevel: 0, status: null }));
  assert.deepEqual(empty, {
    studentNumber: 'Student number is required.',
    firstName: 'First name is required.',
    lastName: 'Last name is required.',
    email: 'E-mail is required.',
    program: 'Select a program.',
    yearLevel: 'Year level must be between 1 and 5.',
    status: 'Select a status.',
  });

  assert.equal(errorsOf(() => ctx.students.create(student({ studentNumber: '23-123' }))).studentNumber, 'Use the format YYYY-NNNNN, e.g. 2023-00123.');
  assert.equal(errorsOf(() => ctx.students.create(student({ email: 'maria.santos@student.example.edu.ph' }))).email,
    'E-mail maria.santos@student.example.edu.ph is already registered.');
  assert.equal(errorsOf(() => ctx.students.create(student({ firstName: 'R2D2' }))).firstName, "First name may only contain letters, spaces, . ' and -.");
  assert.equal(errorsOf(() => ctx.students.create(student({ lastName: 'x'.repeat(51) }))).lastName, 'Last name must be at most 50 characters.');
  assert.equal(errorsOf(() => ctx.students.create(student({ birthDate: '2015-01-01' }))).birthDate, 'Student must be at least 14 years old.');
  assert.equal(errorsOf(() => ctx.students.create(student({ birthDate: '1899-12-31' }))).birthDate, 'Enter a realistic birth date.');
  assert.equal(errorsOf(() => ctx.students.create(student({ yearLevel: 6 }))).yearLevel, 'Year level must be between 1 and 5.');
  assert.equal(errorsOf(() => ctx.students.create(student({ programId: 999 }))).program, 'Program does not exist.');
  // exactly 14 today is allowed (clock = 2026-09-19)
  assert.ok(ctx.students.create(student({ studentNumber: '2026-90001', email: 'fourteen@example.edu.ph', birthDate: '2012-09-19' })).id);
});

test('accepts Filipino names with ñ, hyphens and apostrophes, and +63 numbers', async () => {
  const ctx = await context(false);
  const s = ctx.students.create(student({ firstName: "Ma. Lourdes", lastName: "Ibañez-O'Neil", phone: '+639171234567' }));
  assert.equal(s.lastName, "Ibañez-O'Neil");
  assert.equal(s.phone, '+639171234567');
});

test('editing keeps its own student number and e-mail without a false duplicate', async () => {
  const ctx = await context(true);
  const maria = ctx.students.findByStudentNumber('2023-00123');
  const updated = ctx.students.update({ ...maria, programId: maria.program.id, yearLevel: 5 });
  assert.equal(updated.yearLevel, 5);
  const jose = ctx.students.findByStudentNumber('2023-00145');
  assert.equal(errorsOf(() => ctx.students.update({ ...jose, programId: jose.program.id, studentNumber: '2023-00123' })).studentNumber,
    'Student number 2023-00123 is already in use.');
});

test('search by name, number or e-mail; filters; sorting; LIKE wildcards are literal', async () => {
  const ctx = await context(true);
  assert.equal(ctx.students.search({ keyword: 'santos' }).length, 1);
  assert.equal(ctx.students.search({ keyword: 'maria santos' }).length, 1);
  assert.equal(ctx.students.search({ keyword: '2024-00' }).length, 7);
  assert.equal(ctx.students.search({ keyword: 'ibanez@' }).length, 1);
  assert.equal(ctx.students.search({ keyword: '%' }).length, 0);
  assert.equal(ctx.students.search({ keyword: "' OR 1=1 --" }).length, 0);
  assert.equal(ctx.students.search({ programId: 1 }).length, 10);
  assert.equal(ctx.students.search({ yearLevel: 3 }).length, 7);
  assert.equal(ctx.students.search({ status: 'ACTIVE' }).length, 22);
  assert.equal(ctx.students.search({ programId: 1, yearLevel: 5, status: 'ACTIVE' }).length, 2);
  const byName = ctx.students.search({});
  assert.equal(byName[0].lastName, 'Aguilar');
  assert.equal(byName[24].lastName, 'Villanueva');
  const byNumberDesc = ctx.students.search({ sortBy: 'STUDENT_NUMBER', ascending: false });
  assert.equal(byNumberDesc[0].studentNumber, '2026-00421');
  assert.deepEqual(ctx.students.countByProgram(), [['BSCpE', 10], ['BSCS', 6], ['BSIT', 5], ['BSECE', 4]]);
});

test('suggests the next student number for the current year', async () => {
  const ctx = await context(true);
  assert.equal(ctx.students.suggestNextStudentNumber(), '2026-00422');
  const empty = await context(false);
  assert.equal(empty.students.suggestNextStudentNumber(), '2026-00001');
});

test('normalize and date helpers', () => {
  const n = normalize({ studentNumber: ' 2024-00001 ', firstName: 'Mark   Anthony', lastName: null, email: 'A@B.PH', phone: '', birthDate: '', programId: '2', yearLevel: '3' });
  assert.equal(n.firstName, 'Mark Anthony');
  assert.equal(n.lastName, '');
  assert.equal(n.email, 'a@b.ph');
  assert.equal(n.phone, null);
  assert.equal(n.birthDate, null);
  assert.equal(n.programId, 2);
  assert.ok(isIsoDate('2004-02-29'));
  assert.ok(!isIsoDate('2005-02-29'));
  assert.ok(!isIsoDate('2005-13-01'));
  assert.ok(!isIsoDate('05-06-18'));
});
