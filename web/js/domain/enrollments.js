// Enrollment and grading rules, plus GPA computation (EnrollmentService + JdbcEnrollmentDao in Java).
import { Errors, BusinessRuleError, ValidationError } from './errors.js';
import { likePattern, hasText, today } from './sqlutil.js';
import { SEMESTERS, SEMESTER_SHORT, STUDENT_STATUS_LABELS, term, termKey } from './labels.js';
import { gpa, unitsEarned, isValidGrade, toHundredths } from './grades.js';
import { fullName } from './students.js';

/** e.g. 2025-2026 (the second year must follow the first). */
export const SCHOOL_YEAR = /^(\d{4})-(\d{4})$/;

const SELECT = `
  SELECT e.enrollment_id AS id, e.student_id AS studentId, s.student_number AS studentNumber,
         s.first_name AS firstName, s.last_name AS lastName,
         e.course_id AS courseId, c.course_code AS courseCode, c.title AS courseTitle, c.units,
         e.school_year AS schoolYear, e.semester, e.grade, e.status
  FROM enrollments e
  JOIN students s ON s.student_id = e.student_id
  JOIN courses c ON c.course_id = e.course_id`;

/** Newest term first; semesters in academic order (1st, 2nd, Summer). */
const ORDER_BY = `
  ORDER BY e.school_year DESC,
           CASE e.semester WHEN 'FIRST' THEN 1 WHEN 'SECOND' THEN 2 ELSE 3 END DESC,
           s.last_name, s.first_name, c.course_code`;

function mapRow(r) {
  return {
    id: r.id,
    studentId: r.studentId,
    studentNumber: r.studentNumber,
    studentName: r.firstName + ' ' + r.lastName,
    courseId: r.courseId,
    courseCode: r.courseCode,
    courseTitle: r.courseTitle,
    units: Number(r.units),
    schoolYear: r.schoolYear,
    semester: r.semester,
    grade: r.grade == null ? null : toHundredths(r.grade),
    status: r.status,
    term: term(r.schoolYear, r.semester),
    termKey: termKey(r.schoolYear, r.semester),
  };
}

export class EnrollmentService {
  constructor(db, students, courses, clock = () => new Date()) {
    this.db = db;
    this.students = students;
    this.courses = courses;
    this.clock = clock;
  }

  /** filter: { keyword, courseId, schoolYear, semester }. */
  search(filter = {}) {
    let sql = SELECT + ' WHERE 1 = 1';
    const params = [];
    if (hasText(filter.keyword)) {
      const like = likePattern(filter.keyword);
      sql += `
        AND (LOWER(s.student_number) LIKE ? ESCAPE '!'
             OR LOWER(s.first_name || ' ' || s.last_name) LIKE ? ESCAPE '!'
             OR LOWER(c.course_code) LIKE ? ESCAPE '!'
             OR LOWER(c.title) LIKE ? ESCAPE '!')`;
      for (let i = 0; i < 4; i++) params.push(like);
    }
    if (filter.courseId != null) { sql += ' AND e.course_id = ?'; params.push(filter.courseId); }
    if (hasText(filter.schoolYear)) { sql += ' AND e.school_year = ?'; params.push(filter.schoolYear); }
    if (filter.semester) { sql += ' AND e.semester = ?'; params.push(filter.semester); }
    return this.db.query(sql + ORDER_BY, params).map(mapRow);
  }

  findByStudent(studentId) {
    return this.db.query(SELECT + ' WHERE e.student_id = ?' + ORDER_BY, [studentId]).map(mapRow);
  }

  findGraded() {
    return this.db.query(SELECT + " WHERE e.status = 'COMPLETED'" + ORDER_BY).map(mapRow);
  }

  findById(id) {
    const r = this.db.queryOne(SELECT + ' WHERE e.enrollment_id = ?', [id]);
    return r ? mapRow(r) : null;
  }

  exists(studentId, courseId, schoolYear, semester) {
    return this.db.queryInt('SELECT COUNT(*) FROM enrollments WHERE student_id = ? AND course_id = ? AND school_year = ? AND semester = ?',
      [studentId, courseId, schoolYear, semester]) > 0;
  }

  count() {
    return this.db.queryInt('SELECT COUNT(*) FROM enrollments');
  }

  /**
   * Enrolls a student in a course for a term. Throws a ValidationError if the student is not
   * active, the term is malformed, or the student is already enrolled in that course for that term.
   */
  enroll(studentId, courseId, schoolYear, semester) {
    const errors = new Errors();
    const student = studentId == null ? null : this.students.findById(studentId);
    const course = courseId == null ? null : this.courses.findById(courseId);
    const sy = schoolYear == null ? '' : String(schoolYear).trim();

    if (!student) {
      errors.add('student', 'Select a student.');
    } else if (student.status !== 'ACTIVE') {
      errors.add('student', 'Only active students can be enrolled (' + fullName(student)
        + ' is ' + STUDENT_STATUS_LABELS[student.status].toLowerCase() + ').');
    }
    if (!course) errors.add('course', 'Select a course.');
    if (!isValidSchoolYear(sy)) {
      errors.add('schoolYear', 'Use the format YYYY-YYYY, e.g. ' + currentSchoolYear(today(this.clock)) + '.');
    }
    if (!semester || !SEMESTERS.includes(semester)) errors.add('semester', 'Select a semester.');
    if (!errors.has('student') && !errors.has('course') && !errors.has('schoolYear') && !errors.has('semester')
        && this.exists(studentId, courseId, sy, semester)) {
      errors.add('course', fullName(student) + ' is already enrolled in ' + course.code
        + ' for ' + SEMESTER_SHORT[semester] + ' ' + sy + '.');
    }
    errors.throwIfAny();

    const id = this.db.insert("INSERT INTO enrollments (student_id, course_id, school_year, semester, status) VALUES (?, ?, ?, ?, 'ENROLLED')",
      [studentId, courseId, sy, semester]);
    return this.findById(id);
  }

  /**
   * Records (or clears, when grade is null) the final grade of an enrollment. Grade is in hundredths.
   * A recorded grade marks the enrollment COMPLETED; clearing it returns it to ENROLLED.
   */
  recordGrade(enrollmentId, grade) {
    const e = this.findById(enrollmentId);
    if (!e) throw new BusinessRuleError('Enrollment no longer exists.');
    if (e.status === 'DROPPED') throw new BusinessRuleError('Cannot grade a dropped course.');
    if (grade == null) {
      this.updateGrade(enrollmentId, null, 'ENROLLED');
    } else {
      if (!isValidGrade(grade)) {
        throw new ValidationError({ grade: 'Grade must be 1.00 to 3.00 in steps of 0.25, or 5.00 (failed).' });
      }
      this.updateGrade(enrollmentId, grade, 'COMPLETED');
    }
    return this.findById(enrollmentId);
  }

  /** Marks an in-progress enrollment as dropped. Graded courses cannot be dropped. */
  drop(enrollmentId) {
    const e = this.findById(enrollmentId);
    if (!e) throw new BusinessRuleError('Enrollment no longer exists.');
    if (e.status === 'COMPLETED') {
      throw new BusinessRuleError('Cannot drop ' + e.courseCode + ': it already has a final grade.');
    }
    this.updateGrade(enrollmentId, null, 'DROPPED');
    return this.findById(enrollmentId);
  }

  updateGrade(enrollmentId, grade, status) {
    return this.db.run('UPDATE enrollments SET grade = ?, status = ? WHERE enrollment_id = ?',
      [grade == null ? null : grade / 100, status, enrollmentId]) === 1;
  }

  delete(enrollmentId) {
    if (this.db.run('DELETE FROM enrollments WHERE enrollment_id = ?', [enrollmentId]) !== 1) {
      throw new BusinessRuleError('Enrollment no longer exists.');
    }
  }

  /** Full transcript of a student with cumulative and per-term GPA. */
  academicRecord(studentId) {
    const student = this.students.findById(studentId);
    if (!student) throw new BusinessRuleError('Student no longer exists.');
    const enrollments = this.findByStudent(studentId);

    const byTerm = new Map();
    for (const e of enrollments) {
      if (!byTerm.has(e.termKey)) byTerm.set(e.termKey, []);
      byTerm.get(e.termKey).push(e);
    }
    const terms = [...byTerm.keys()].sort().map((key) => {
      const list = byTerm.get(key);
      const counted = list.filter((e) => e.status !== 'DROPPED');
      return {
        term: list[0].term,
        courses: counted.length,
        units: counted.reduce((sum, e) => sum + e.units, 0),
        gpa: gpa(list),
      };
    });
    return { student, enrollments, gpa: gpa(enrollments), unitsEarned: unitsEarned(enrollments), terms };
  }

  /** Cumulative GPA of every student that has at least one graded course: Map<studentId, hundredths>. */
  gpaByStudent() {
    const grouped = new Map();
    for (const e of this.findGraded()) {
      if (!grouped.has(e.studentId)) grouped.set(e.studentId, []);
      grouped.get(e.studentId).push(e);
    }
    const result = new Map();
    for (const [id, list] of grouped) {
      const g = gpa(list);
      if (g != null) result.set(id, g);
    }
    return result;
  }

  /** School years with enrollments plus the current one, newest first. */
  schoolYears() {
    const years = this.db.query('SELECT DISTINCT school_year AS sy FROM enrollments ORDER BY school_year DESC').map((r) => r.sy);
    const current = this.currentSchoolYear();
    if (!years.includes(current)) years.push(current);
    return years.sort().reverse();
  }

  currentSchoolYear() {
    return currentSchoolYear(today(this.clock));
  }

  currentSemester() {
    return currentSemester(today(this.clock));
  }
}

/** Philippine school years start in August: Aug 2026 - Jul 2027 is "2026-2027". */
export function currentSchoolYear({ year, month }) {
  const start = month >= 8 ? year : year - 1;
  return start + '-' + (start + 1);
}

/** Aug-Dec = 1st semester, Jan-May = 2nd semester, Jun-Jul = summer (midyear) term. */
export function currentSemester({ month }) {
  if (month >= 8) return 'FIRST';
  return month <= 5 ? 'SECOND' : 'SUMMER';
}

export function isValidSchoolYear(schoolYear) {
  if (schoolYear == null) return false;
  const m = SCHOOL_YEAR.exec(String(schoolYear).trim());
  if (!m) return false;
  const start = parseInt(m[1], 10);
  const end = parseInt(m[2], 10);
  return end === start + 1 && start >= 1900 && start <= 2100;
}
