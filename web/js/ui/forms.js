// Add/edit dialogs. All business validation happens in the services; the dialogs only map errors to fields.
import { formDialog } from './dialogs.js';
import { STUDENT_STATUSES, STUDENT_STATUS_LABELS, SEMESTERS, SEMESTER_LABELS, yearLevelLabel } from '../domain/labels.js';
import { VALID_GRADES, formatGrade, describeGrade, toHundredths } from '../domain/grades.js';
import { fullName, sortableName, isIsoDate } from '../domain/students.js';
import { MIN_UNITS, MAX_UNITS } from '../domain/courses.js';

export function studentDialog(ctx, student = null) {
  const programs = ctx.students.programs();
  return formDialog({
    title: student ? 'Edit student' : 'Add student',
    subtitle: student ? fullName(student) + ' · ' + student.studentNumber : 'Create a new student record. Fields marked * are required.',
    submitLabel: student ? 'Save Changes' : 'Add Student',
    width: 600,
    fields: [
      { key: 'studentNumber', label: 'Student number', required: true, value: student ? student.studentNumber : ctx.students.suggestNextStudentNumber(), placeholder: '2026-00001', autofocus: !!student },
      { key: 'status', label: 'Status', required: true, type: 'select', value: student ? student.status : 'ACTIVE', options: STUDENT_STATUSES.map((s) => ({ value: s, label: STUDENT_STATUS_LABELS[s] })) },
      { key: 'firstName', label: 'First name', required: true, value: student?.firstName ?? '', autofocus: !student },
      { key: 'lastName', label: 'Last name', required: true, value: student?.lastName ?? '' },
      { key: 'email', label: 'E-mail', required: true, full: true, type: 'email', value: student?.email ?? '', placeholder: 'name@student.example.edu.ph' },
      { key: 'phone', label: 'Mobile number', value: student?.phone ?? '', placeholder: '09171234567 (optional)', inputmode: 'tel' },
      { key: 'birthDate', label: 'Birth date', value: student?.birthDate ?? '', placeholder: 'YYYY-MM-DD (optional)', inputmode: 'numeric' },
      { key: 'program', label: 'Program', required: true, type: 'select', value: student ? student.program.id : programs[0]?.id, options: programs.map((p) => ({ value: p.id, label: p.name })) },
      { key: 'yearLevel', label: 'Year level', required: true, type: 'select', value: student ? student.yearLevel : 1, options: [1, 2, 3, 4, 5].map((y) => ({ value: y, label: yearLevelLabel(y) })) },
    ],
    validate: (v) => {
      const text = v.birthDate.trim();
      if (text && !isIsoDate(text)) return { birthDate: 'Use the format YYYY-MM-DD, e.g. 2005-06-18.' };
      return null;
    },
    onSubmit: (v) => {
      const data = {
        id: student ? student.id : null,
        studentNumber: v.studentNumber, firstName: v.firstName, lastName: v.lastName, email: v.email,
        phone: v.phone, birthDate: v.birthDate, programId: v.program, yearLevel: Number(v.yearLevel), status: v.status,
      };
      return student ? ctx.students.update(data) : ctx.students.create(data);
    },
  });
}

export function courseDialog(ctx, course = null) {
  return formDialog({
    title: course ? 'Edit course' : 'Add course',
    subtitle: course ? course.code + ' · ' + course.title : 'Add a course to the catalogue. Fields marked * are required.',
    submitLabel: course ? 'Save Changes' : 'Add Course',
    width: 560,
    fields: [
      { key: 'code', label: 'Course code', required: true, value: course?.code ?? '', placeholder: 'e.g. CPE 301', autofocus: true },
      { key: 'units', label: 'Units', required: true, type: 'number', value: course?.units ?? 3, min: MIN_UNITS, max: MAX_UNITS, step: 1, inputmode: 'numeric' },
      { key: 'title', label: 'Title', required: true, full: true, value: course?.title ?? '', placeholder: 'e.g. Microprocessors and Microcontrollers' },
      { key: 'description', label: 'Description', full: true, value: course?.description ?? '', placeholder: 'Short description (optional)' },
    ],
    onSubmit: (v) => {
      const data = { id: course ? course.id : null, code: v.code, title: v.title, units: v.units === '' ? 0 : Number(v.units), description: v.description };
      return course ? ctx.courses.update(data) : ctx.courses.create(data);
    },
  });
}

export function enrollDialog(ctx, preselectedStudentId = null) {
  const students = ctx.students.search({ status: 'ACTIVE' });
  const courses = ctx.courses.findAll();
  return formDialog({
    title: 'Enroll student',
    subtitle: 'Only active students can be enrolled. A student can take a course once per term.',
    submitLabel: 'Enroll',
    width: 620,
    fields: [
      { key: 'student', label: 'Student', required: true, full: true, type: 'select', value: preselectedStudentId ?? students[0]?.id,
        options: students.map((s) => ({ value: s.id, label: sortableName(s) + '  ·  ' + s.studentNumber + '  ·  ' + s.program.code })) },
      { key: 'course', label: 'Course', required: true, full: true, type: 'select', value: courses[0]?.id,
        options: courses.map((c) => ({ value: c.id, label: c.code + '  –  ' + c.title + '  (' + c.units + ' units)' })) },
      { key: 'schoolYear', label: 'School year', required: true, value: ctx.enrollments.currentSchoolYear(), placeholder: 'YYYY-YYYY' },
      { key: 'semester', label: 'Semester', required: true, type: 'select', value: ctx.enrollments.currentSemester(), options: SEMESTERS.map((s) => ({ value: s, label: SEMESTER_LABELS[s] })) },
    ],
    onSubmit: (v) => ctx.enrollments.enroll(v.student === '' ? null : Number(v.student), v.course === '' ? null : Number(v.course), v.schoolYear, v.semester),
  });
}

export function gradeDialog(ctx, enrollment) {
  return formDialog({
    title: 'Record grade',
    subtitle: enrollment.studentName + ' · ' + enrollment.courseCode + ' · ' + enrollment.term,
    submitLabel: 'Save Grade',
    width: 520,
    fields: [
      { key: 'course', label: 'Course', type: 'readonly', full: true, value: enrollment.courseCode + ' – ' + enrollment.courseTitle },
      { key: 'units', label: 'Units', type: 'readonly', value: enrollment.units + ' units' },
      { key: 'grade', label: 'Final grade', type: 'select', value: enrollment.grade ?? '', autofocus: true,
        options: [{ value: '', label: 'No grade yet (in progress)' }, ...VALID_GRADES.map((g) => ({ value: g, label: formatGrade(g) + '   –   ' + describeGrade(g) }))] },
    ],
    onSubmit: (v) => ctx.enrollments.recordGrade(enrollment.id, v.grade === '' ? null : toHundredths(Number(v.grade) / 100)),
  });
}
