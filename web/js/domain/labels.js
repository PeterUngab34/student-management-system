// Enum labels shared by the services and the UI (StudentStatus, EnrollmentStatus, Semester in Java).

export const STUDENT_STATUSES = ['ACTIVE', 'ON_LEAVE', 'INACTIVE', 'GRADUATED'];
export const STUDENT_STATUS_LABELS = {
  ACTIVE: 'Active',
  ON_LEAVE: 'On leave',
  INACTIVE: 'Inactive',
  GRADUATED: 'Graduated',
};

export const ENROLLMENT_STATUS_LABELS = {
  ENROLLED: 'Enrolled',
  COMPLETED: 'Completed',
  DROPPED: 'Dropped',
};

/** Declaration order is chronological. */
export const SEMESTERS = ['FIRST', 'SECOND', 'SUMMER'];
export const SEMESTER_LABELS = { FIRST: '1st Semester', SECOND: '2nd Semester', SUMMER: 'Summer' };
export const SEMESTER_SHORT = { FIRST: '1st Sem', SECOND: '2nd Sem', SUMMER: 'Summer' };

export function yearLevelLabel(y) {
  switch (y) {
    case 1: return '1st Year';
    case 2: return '2nd Year';
    case 3: return '3rd Year';
    default: return y + 'th Year';
  }
}

/** e.g. "1st Sem 2025-2026". */
export function term(schoolYear, semester) {
  return SEMESTER_SHORT[semester] + ' ' + schoolYear;
}

/** Sort key that orders terms chronologically, e.g. "2025-2026#0". */
export function termKey(schoolYear, semester) {
  return schoolYear + '#' + SEMESTERS.indexOf(semester);
}
