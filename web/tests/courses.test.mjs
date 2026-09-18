import { test } from 'node:test';
import assert from 'node:assert/strict';
import { context } from './helper.mjs';
import { ValidationError, BusinessRuleError } from '../js/domain/errors.js';
import { normalizeCode } from '../js/domain/courses.js';

test('course codes are normalized: "cpe301 " -> "CPE 301"', () => {
  assert.equal(normalizeCode('cpe301 '), 'CPE 301');
  assert.equal(normalizeCode('  math   201'), 'MATH 201');
  assert.equal(normalizeCode('cs 211l'), 'CS 211L');
});

test('create, search, update', async () => {
  const ctx = await context(true);
  const c = ctx.courses.create({ code: 'cpe 499', title: '  Capstone   Project ', units: 3, description: '  ' });
  assert.equal(c.code, 'CPE 499');
  assert.equal(c.title, 'Capstone Project');
  assert.equal(c.description, null);
  assert.equal(ctx.courses.count(), 11);
  assert.equal(ctx.courses.search('capstone').length, 1);
  assert.equal(ctx.courses.search('cpe').length, 6);
  const u = ctx.courses.update({ ...c, units: 4 });
  assert.equal(u.units, 4);
});

test('validation rules and messages', async () => {
  const ctx = await context(true);
  const errors = (fn) => { try { fn(); } catch (e) { if (e instanceof ValidationError) return e.errors; throw e; } assert.fail('expected ValidationError'); };
  assert.deepEqual(errors(() => ctx.courses.create({ code: '', title: '', units: 0, description: 'x'.repeat(256) })), {
    code: 'Course code is required.',
    title: 'Title is required.',
    units: 'Units must be between 1 and 6.',
    description: 'Description must be at most 255 characters.',
  });
  assert.equal(errors(() => ctx.courses.create({ code: '301', title: 'X', units: 3 })).code, 'Use a code like CPE 301 or MATH 201.');
  assert.equal(errors(() => ctx.courses.create({ code: 'cpe301', title: 'X', units: 3 })).code, 'Course code CPE 301 already exists.');
  assert.equal(errors(() => ctx.courses.create({ code: 'CPE 302', title: 'x'.repeat(101), units: 3 })).title, 'Title must be at most 100 characters.');
});

test('a course with enrollment history cannot be deleted; an unused one can', async () => {
  const ctx = await context(true);
  const cpe201 = ctx.courses.findByCode('CPE 201');
  assert.throws(() => ctx.courses.delete(cpe201.id), (e) =>
    e instanceof BusinessRuleError && e.message === 'CPE 201 has 18 enrollment record(s) and cannot be deleted. Remove those enrollments first.');
  assert.equal(ctx.courses.count(), 10);
  const fresh = ctx.courses.create({ code: 'GE 101', title: 'Understanding the Self', units: 3 });
  ctx.courses.delete(fresh.id);
  assert.equal(ctx.courses.count(), 10);
  assert.throws(() => ctx.courses.delete(fresh.id), BusinessRuleError);
  assert.equal(ctx.courses.enrollmentCounts().get(cpe201.id), 18);
});
