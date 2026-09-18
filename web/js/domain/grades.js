// The Philippine 1.00 - 5.00 grading scale and GPA arithmetic (GradeScale + GradeCalculator in Java).
// Grades are handled as integers in hundredths so the math is exact, like BigDecimal.

export const HIGHEST = 100;
export const PASSING = 300;
export const FAILED = 500;

/** Every grade that may be recorded, best to worst, in hundredths. */
export const VALID_GRADES = [100, 125, 150, 175, 200, 225, 250, 275, 300, 500];

/** "1.75" or 1.75 -> 175; null/'' -> null. */
export function toHundredths(value) {
  if (value == null || value === '') return null;
  const n = typeof value === 'number' ? value : Number(String(value).trim());
  if (!Number.isFinite(n)) return NaN;
  return Math.round(n * 100);
}

export function isValidGrade(hundredths) {
  return hundredths != null && VALID_GRADES.includes(hundredths);
}

export function isPassing(hundredths) {
  return hundredths != null && hundredths <= PASSING;
}

export function describeGrade(hundredths) {
  if (hundredths == null) return '';
  if (hundredths === HIGHEST) return 'Excellent';
  if (hundredths <= 150) return 'Very good';
  if (hundredths <= 225) return 'Good';
  if (hundredths <= 275) return 'Satisfactory';
  return isPassing(hundredths) ? 'Passing' : 'Failed';
}

/** Label used for grade values in tables and charts: "1.75", or an em dash for none. */
export function formatGrade(hundredths) {
  if (hundredths == null) return '—';
  const sign = hundredths < 0 ? '-' : '';
  const abs = Math.abs(hundredths);
  return sign + Math.floor(abs / 100) + '.' + String(abs % 100).padStart(2, '0');
}

/** Rounds a rational numerator/denominator to the nearest integer, half-up (like RoundingMode.HALF_UP). */
function divideHalfUp(numerator, denominator) {
  return Math.floor((2 * numerator + denominator) / (2 * denominator));
}

/**
 * GPA = sum(grade x units) / sum(units) over graded (COMPLETED) enrollments only,
 * rounded half-up to two decimals. Returns hundredths, or null when nothing is graded.
 * Each enrollment needs { grade (hundredths|null), units, status }.
 */
export function gpa(enrollments) {
  let weighted = 0;
  let units = 0;
  for (const e of enrollments) {
    if (isGraded(e)) {
      weighted += e.grade * e.units;
      units += e.units;
    }
  }
  if (units === 0) return null;
  return divideHalfUp(weighted, units);
}

export function isGraded(e) {
  return e.status === 'COMPLETED' && e.grade != null;
}

/** Units of courses completed with a passing grade (3.00 or better). */
export function unitsEarned(enrollments) {
  return enrollments.filter((e) => isGraded(e) && isPassing(e.grade)).reduce((sum, e) => sum + e.units, 0);
}

/** Simple average of several GPAs (hundredths), rounded half-up to two decimals. */
export function average(values) {
  if (!values.length) return null;
  return divideHalfUp(values.reduce((a, b) => a + b, 0), values.length);
}

export function remarks(e) {
  if (e.status === 'DROPPED') return 'Dropped';
  if (e.grade == null) return 'In progress';
  return isPassing(e.grade) ? 'Passed' : 'Failed';
}
