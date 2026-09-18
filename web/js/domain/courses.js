// Course catalogue rules (CourseService + JdbcCourseDao in Java).
import { Errors, BusinessRuleError } from './errors.js';
import { likePattern, hasText } from './sqlutil.js';

/** Subject prefix + number, e.g. "CPE 301", "MATH 201", "CS 211L". */
export const COURSE_CODE = /^[A-Z]{2,5} \d{2,4}[A-Z]?$/;
export const MIN_UNITS = 1;
export const MAX_UNITS = 6;

const SELECT = 'SELECT course_id AS id, course_code AS code, title, units, description FROM courses';

function mapRow(r) {
  return { id: r.id, code: r.code, title: r.title, units: Number(r.units), description: r.description ?? null };
}

export class CourseService {
  constructor(db) {
    this.db = db;
  }

  search(keyword) {
    if (!hasText(keyword)) {
      return this.db.query(SELECT + ' ORDER BY course_code').map(mapRow);
    }
    const like = likePattern(keyword);
    return this.db.query(SELECT + " WHERE LOWER(course_code) LIKE ? ESCAPE '!' OR LOWER(title) LIKE ? ESCAPE '!' ORDER BY course_code",
      [like, like]).map(mapRow);
  }

  findAll() {
    return this.search(null);
  }

  findById(id) {
    const r = this.db.queryOne(SELECT + ' WHERE course_id = ?', [id]);
    return r ? mapRow(r) : null;
  }

  findByCode(code) {
    const r = this.db.queryOne(SELECT + ' WHERE UPPER(course_code) = UPPER(?)', [code]);
    return r ? mapRow(r) : null;
  }

  count() {
    return this.db.queryInt('SELECT COUNT(*) FROM courses');
  }

  /** Map of course id -> number of enrollment records. */
  enrollmentCounts() {
    const map = new Map();
    for (const r of this.db.query('SELECT course_id, COUNT(*) AS n FROM enrollments GROUP BY course_id')) {
      map.set(r.course_id, Number(r.n));
    }
    return map;
  }

  countByCourse(courseId) {
    return this.db.queryInt('SELECT COUNT(*) FROM enrollments WHERE course_id = ?', [courseId]);
  }

  create(input) {
    const c = normalize(input);
    this.validate(c, null);
    const id = this.db.insert('INSERT INTO courses (course_code, title, units, description) VALUES (?, ?, ?, ?)',
      [c.code, c.title, c.units, c.description]);
    return this.findById(id);
  }

  update(input) {
    if (input.id == null) throw new Error('Cannot update a course that has not been saved');
    const c = normalize(input);
    this.validate(c, c.id);
    const changed = this.db.run('UPDATE courses SET course_code = ?, title = ?, units = ?, description = ? WHERE course_id = ?',
      [c.code, c.title, c.units, c.description, c.id]);
    if (changed !== 1) throw new BusinessRuleError('Course no longer exists. It may have been deleted.');
    return this.findById(c.id);
  }

  /** Courses with enrollment history cannot be deleted - grades must never silently disappear. */
  delete(courseId) {
    const course = this.findById(courseId);
    if (!course) throw new BusinessRuleError('Course no longer exists. It may have been deleted.');
    const enrollments = this.countByCourse(courseId);
    if (enrollments > 0) {
      throw new BusinessRuleError(course.code + ' has ' + enrollments
        + ' enrollment record(s) and cannot be deleted. Remove those enrollments first.');
    }
    this.db.run('DELETE FROM courses WHERE course_id = ?', [courseId]);
  }

  validate(c, existingId) {
    const errors = new Errors();
    if (c.code === '') {
      errors.add('code', 'Course code is required.');
    } else if (!COURSE_CODE.test(c.code)) {
      errors.add('code', 'Use a code like CPE 301 or MATH 201.');
    } else {
      const existing = this.findByCode(c.code);
      if (existing && existing.id !== existingId) {
        errors.add('code', 'Course code ' + c.code + ' already exists.');
      }
    }
    if (c.title === '') {
      errors.add('title', 'Title is required.');
    } else if (c.title.length > 100) {
      errors.add('title', 'Title must be at most 100 characters.');
    }
    if (!(c.units >= MIN_UNITS && c.units <= MAX_UNITS)) {
      errors.add('units', 'Units must be between ' + MIN_UNITS + ' and ' + MAX_UNITS + '.');
    }
    if (c.description != null && c.description.length > 255) {
      errors.add('description', 'Description must be at most 255 characters.');
    }
    errors.throwIfAny();
  }
}

/** "cpe301 " becomes "CPE 301". */
export function normalizeCode(code) {
  if (code == null) return '';
  const c = String(code).trim().toUpperCase().replace(/\s+/g, ' ');
  return c.replace(/^([A-Z]+)\s*(\d)/, '$1 $2');
}

export function normalize(c) {
  const title = c.title == null ? '' : String(c.title).trim().replace(/\s+/g, ' ');
  const description = c.description == null || String(c.description).trim() === '' ? null : String(c.description).trim();
  return { id: c.id ?? null, code: normalizeCode(c.code), title, units: Number(c.units), description };
}
