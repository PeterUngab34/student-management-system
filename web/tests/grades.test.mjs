import { test } from 'node:test';
import assert from 'node:assert/strict';
import { gpa, unitsEarned, average, formatGrade, describeGrade, isValidGrade, isPassing, toHundredths, remarks } from '../js/domain/grades.js';

const completed = (grade, units) => ({ grade, units, status: 'COMPLETED' });

test('GPA is unit-weighted and rounded half-up to two decimals', () => {
  // 3 x 1.25 + 3 x 2.00 + 4 x 1.75 = 16.75 / 10 = 1.675 -> 1.68 (same figures as the Java test)
  assert.equal(gpa([completed(125, 3), completed(200, 3), completed(175, 4)]), 168);
  // (3.75 + 6) / 6 = 1.625 -> 1.63
  assert.equal(gpa([completed(125, 3), completed(200, 3)]), 163);
  // 1.125 must round UP to 1.13 (half-up), not to 1.12 (banker's rounding)
  assert.equal(gpa([completed(100, 3), completed(125, 1), completed(125, 3), completed(100, 1)]), gpa([completed(100, 4), completed(125, 4)]));
  assert.equal(gpa([completed(100, 1), completed(125, 1)]), 113);
  assert.equal(average([100, 125]), 113);
});

test('only completed courses count; in-progress and dropped are ignored; failed courses count toward GPA but not units', () => {
  const list = [completed(500, 3), completed(100, 3), { grade: null, units: 3, status: 'ENROLLED' }, { grade: null, units: 3, status: 'DROPPED' }];
  assert.equal(gpa(list), 300);
  assert.equal(unitsEarned(list), 3);
  assert.equal(gpa([{ grade: null, units: 3, status: 'ENROLLED' }]), null);
  assert.equal(average([]), null);
});

test('grade scale validation, formatting and descriptions match the desktop app', () => {
  for (const ok of ['1.00', '1.25', '1.5', '1.75', '2', '2.25', '2.50', '2.75', '3.00', '5.00']) {
    assert.ok(isValidGrade(toHundredths(ok)), ok);
  }
  for (const bad of ['0.75', '1.10', '3.25', '4.00', '5.25', 'abc']) {
    assert.ok(!isValidGrade(toHundredths(bad)), bad);
  }
  assert.equal(formatGrade(175), '1.75');
  assert.equal(formatGrade(300), '3.00');
  assert.equal(formatGrade(null), '—');
  assert.equal(describeGrade(100), 'Excellent');
  assert.equal(describeGrade(150), 'Very good');
  assert.equal(describeGrade(225), 'Good');
  assert.equal(describeGrade(275), 'Satisfactory');
  assert.equal(describeGrade(300), 'Passing');
  assert.equal(describeGrade(500), 'Failed');
  assert.ok(isPassing(300) && !isPassing(500));
  assert.equal(remarks({ status: 'DROPPED', grade: null }), 'Dropped');
  assert.equal(remarks({ status: 'ENROLLED', grade: null }), 'In progress');
  assert.equal(remarks({ status: 'COMPLETED', grade: 275 }), 'Passed');
  assert.equal(remarks({ status: 'COMPLETED', grade: 500 }), 'Failed');
});
