// Student records: normalization, validation and uniqueness rules (StudentService + JdbcStudentDao in Java).
import { Errors, BusinessRuleError } from './errors.js';
import { likePattern, hasText, today } from './sqlutil.js';
import { STUDENT_STATUSES } from './labels.js';

/** YYYY-NNNNN, e.g. 2023-00123. */
export const STUDENT_NUMBER = /^(19|20)\d{2}-\d{5}$/;
export const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
/** Letters (incl. Ñ and accents), spaces, periods, apostrophes and hyphens. */
export const NAME = /^\p{L}[\p{L} .'-]*$/u;
/** Philippine mobile number: 09XXXXXXXXX or +639XXXXXXXXX. */
export const PH_MOBILE = /^(09|\+639)\d{9}$/;
export const MIN_AGE = 14;

const SELECT = `
  SELECT s.student_id AS id, s.student_number AS studentNumber, s.first_name AS firstName,
         s.last_name AS lastName, s.email, s.phone, s.birth_date AS birthDate,
         s.year_level AS yearLevel, s.status,
         p.program_id AS programId, p.code AS programCode, p.name AS programName, p.department
  FROM students s
  JOIN programs p ON p.program_id = s.program_id`;

const KEYWORD_CONDITION = `
   AND (LOWER(s.student_number) LIKE ? ESCAPE '!'
        OR LOWER(s.first_name) LIKE ? ESCAPE '!'
        OR LOWER(s.last_name) LIKE ? ESCAPE '!'
        OR LOWER(s.first_name || ' ' || s.last_name) LIKE ? ESCAPE '!'
        OR LOWER(s.email) LIKE ? ESCAPE '!')`;

/** Columns the student list can be ordered by. The SQL fragment never comes from user input. */
const SORT = {
  STUDENT_NUMBER: (dir) => `s.student_number ${dir}`,
  NAME: (dir) => `s.last_name ${dir}, s.first_name ${dir}`,
  PROGRAM: (dir) => `p.code ${dir}, s.last_name ${dir}`,
  YEAR_LEVEL: (dir) => `s.year_level ${dir}, s.last_name ${dir}`,
};

function mapRow(r) {
  return {
    id: r.id,
    studentNumber: r.studentNumber,
    firstName: r.firstName,
    lastName: r.lastName,
    email: r.email,
    phone: r.phone ?? null,
    birthDate: r.birthDate ?? null,
    program: { id: r.programId, code: r.programCode, name: r.programName, department: r.department },
    yearLevel: Number(r.yearLevel),
    status: r.status,
  };
}

export function fullName(s) {
  return s.firstName + ' ' + s.lastName;
}

/** "Last, First" - the conventional ordering for class lists. */
export function sortableName(s) {
  return s.lastName + ', ' + s.firstName;
}

export function initials(s) {
  return (s.firstName ? s.firstName[0] : '') + (s.lastName ? s.lastName[0] : '');
}

export class StudentService {
  constructor(db, clock = () => new Date()) {
    this.db = db;
    this.clock = clock;
  }

  programs() {
    return this.db.query('SELECT program_id AS id, code, name, department FROM programs ORDER BY program_id');
  }

  findProgram(id) {
    return this.db.queryOne('SELECT program_id AS id, code, name, department FROM programs WHERE program_id = ?', [id]);
  }

  /** filter: { keyword, programId, yearLevel, status, sortBy, ascending } - every criterion optional. */
  search(filter = {}) {
    let sql = SELECT + ' WHERE 1 = 1';
    const params = [];
    if (hasText(filter.keyword)) {
      const like = likePattern(filter.keyword);
      sql += KEYWORD_CONDITION;
      for (let i = 0; i < 5; i++) params.push(like);
    }
    if (filter.programId != null) { sql += ' AND s.program_id = ?'; params.push(filter.programId); }
    if (filter.yearLevel != null) { sql += ' AND s.year_level = ?'; params.push(filter.yearLevel); }
    if (filter.status) { sql += ' AND s.status = ?'; params.push(filter.status); }
    const sort = SORT[filter.sortBy] || SORT.NAME;
    sql += ' ORDER BY ' + sort(filter.ascending === false ? 'DESC' : 'ASC');
    return this.db.query(sql, params).map(mapRow);
  }

  findAll() {
    return this.search({});
  }

  findById(id) {
    const r = this.db.queryOne(SELECT + ' WHERE s.student_id = ?', [id]);
    return r ? mapRow(r) : null;
  }

  findByStudentNumber(n) {
    const r = this.db.queryOne(SELECT + ' WHERE s.student_number = ?', [n]);
    return r ? mapRow(r) : null;
  }

  findByEmail(email) {
    const r = this.db.queryOne(SELECT + ' WHERE LOWER(s.email) = LOWER(?)', [email]);
    return r ? mapRow(r) : null;
  }

  count() {
    return this.db.queryInt('SELECT COUNT(*) FROM students');
  }

  countByProgram() {
    return this.db.query(`
      SELECT p.code, COUNT(s.student_id) AS total
      FROM programs p
      LEFT JOIN students s ON s.program_id = p.program_id
      GROUP BY p.code
      ORDER BY total DESC, p.code`).map((r) => [r.code, Number(r.total)]);
  }

  create(input) {
    const s = normalize(input);
    this.validate(s, null);
    const id = this.db.insert(`
      INSERT INTO students (student_number, first_name, last_name, email, phone, birth_date, program_id, year_level, status)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [s.studentNumber, s.firstName, s.lastName, s.email, s.phone, s.birthDate, s.programId, s.yearLevel, s.status]);
    return this.findById(id);
  }

  update(input) {
    if (input.id == null) throw new Error('Cannot update a student that has not been saved');
    const s = normalize(input);
    this.validate(s, s.id);
    const changed = this.db.run(`
      UPDATE students
      SET student_number = ?, first_name = ?, last_name = ?, email = ?, phone = ?, birth_date = ?,
          program_id = ?, year_level = ?, status = ?, updated_at = CURRENT_TIMESTAMP
      WHERE student_id = ?`,
      [s.studentNumber, s.firstName, s.lastName, s.email, s.phone, s.birthDate, s.programId, s.yearLevel, s.status, s.id]);
    if (changed !== 1) throw new BusinessRuleError('Student no longer exists. It may have been deleted.');
    return this.findById(s.id);
  }

  /** Deletes the student together with their enrollments (ON DELETE CASCADE). */
  delete(id) {
    if (this.db.run('DELETE FROM students WHERE student_id = ?', [id]) !== 1) {
      throw new BusinessRuleError('Student no longer exists. It may have been deleted.');
    }
  }

  /** Suggests the next free student number for the current year, e.g. 2026-00422. */
  suggestNextStudentNumber() {
    const prefix = today(this.clock).year + '-';
    const max = this.db.queryValue('SELECT MAX(student_number) FROM students WHERE student_number LIKE ?', [prefix + '%']);
    const next = max ? parseInt(String(max).substring(prefix.length), 10) + 1 : 1;
    return prefix + String(next).padStart(5, '0');
  }

  validate(s, existingId) {
    const errors = new Errors();

    if (s.studentNumber === '') {
      errors.add('studentNumber', 'Student number is required.');
    } else if (!STUDENT_NUMBER.test(s.studentNumber)) {
      errors.add('studentNumber', 'Use the format YYYY-NNNNN, e.g. 2023-00123.');
    } else if (takenByAnother(this.findByStudentNumber(s.studentNumber), existingId)) {
      errors.add('studentNumber', 'Student number ' + s.studentNumber + ' is already in use.');
    }

    validateName(errors, 'firstName', 'First name', s.firstName);
    validateName(errors, 'lastName', 'Last name', s.lastName);

    if (s.email === '') {
      errors.add('email', 'E-mail is required.');
    } else if (s.email.length > 100 || !EMAIL.test(s.email)) {
      errors.add('email', 'Enter a valid e-mail address.');
    } else if (takenByAnother(this.findByEmail(s.email), existingId)) {
      errors.add('email', 'E-mail ' + s.email + ' is already registered.');
    }

    if (s.phone != null && !PH_MOBILE.test(s.phone)) {
      errors.add('phone', 'Use a PH mobile number, e.g. 09171234567.');
    }

    if (s.birthDate != null) {
      const t = today(this.clock);
      const minAge = shiftYears(t.iso, -MIN_AGE);
      if (s.birthDate > t.iso) {
        errors.add('birthDate', 'Birth date cannot be in the future.');
      } else if (s.birthDate > minAge) {
        errors.add('birthDate', 'Student must be at least ' + MIN_AGE + ' years old.');
      } else if (s.birthDate < '1900-01-01') {
        errors.add('birthDate', 'Enter a realistic birth date.');
      }
    }

    if (s.programId == null) {
      errors.add('program', 'Select a program.');
    } else if (!this.findProgram(s.programId)) {
      errors.add('program', 'Program does not exist.');
    }

    if (!(s.yearLevel >= 1 && s.yearLevel <= 5)) {
      errors.add('yearLevel', 'Year level must be between 1 and 5.');
    }
    if (!s.status || !STUDENT_STATUSES.includes(s.status)) {
      errors.add('status', 'Select a status.');
    }
    errors.throwIfAny();
  }
}

/** Trims and canonicalizes user input before validation. */
export function normalize(s) {
  const phone = trimToEmpty(s.phone).replace(/[\s-]/g, '');
  return {
    id: s.id ?? null,
    studentNumber: trimToEmpty(s.studentNumber),
    firstName: collapseSpaces(s.firstName),
    lastName: collapseSpaces(s.lastName),
    email: trimToEmpty(s.email).toLowerCase(),
    phone: phone === '' ? null : phone,
    birthDate: hasText(s.birthDate) ? String(s.birthDate).trim() : null,
    programId: s.programId == null || s.programId === '' ? null : Number(s.programId),
    yearLevel: Number(s.yearLevel),
    status: s.status ?? null,
  };
}

/** Same date N years later/earlier (LocalDate.minusYears semantics incl. Feb 29 -> Feb 28). */
function shiftYears(iso, years) {
  const [y, m, d] = iso.split('-').map(Number);
  const ny = y + years;
  const lastDay = new Date(ny, m, 0).getDate();
  return ny + '-' + String(m).padStart(2, '0') + '-' + String(Math.min(d, lastDay)).padStart(2, '0');
}

function validateName(errors, field, label, value) {
  if (value === '') {
    errors.add(field, label + ' is required.');
  } else if (value.length > 50) {
    errors.add(field, label + ' must be at most 50 characters.');
  } else if (!NAME.test(value)) {
    errors.add(field, label + " may only contain letters, spaces, . ' and -.");
  }
}

function takenByAnother(found, existingId) {
  return found != null && found.id !== existingId;
}

function trimToEmpty(s) {
  return s == null ? '' : String(s).trim();
}

function collapseSpaces(s) {
  return trimToEmpty(s).replace(/\s+/g, ' ');
}

/** Strict YYYY-MM-DD check used by the form before the service (LocalDate.parse in the dialog). */
export function isIsoDate(text) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) return false;
  const [y, m, d] = text.split('-').map(Number);
  if (m < 1 || m > 12) return false;
  return d >= 1 && d <= new Date(y, m, 0).getDate();
}
