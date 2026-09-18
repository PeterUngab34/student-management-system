// Aggregates the numbers shown on the dashboard (DashboardService in Java).
import { VALID_GRADES, formatGrade, average } from './grades.js';
import { SEMESTER_SHORT } from './labels.js';
import { sortableName } from './students.js';

/** GPA at or better than this makes a student a Dean's List candidate (hundredths). */
export const DEANS_LIST_GPA = 175;

export class DashboardService {
  constructor(students, courses, enrollments) {
    this.students = students;
    this.courses = courses;
    this.enrollments = enrollments;
  }

  load() {
    const students = this.students.findAll();
    const byId = new Map(students.map((s) => [s.id, s]));
    const gpas = this.enrollments.gpaByStudent();

    // grade distribution across all graded enrollments, one bucket per valid grade
    const distribution = VALID_GRADES.map((g) => [formatGrade(g), 0]);
    const index = new Map(distribution.map((entry, i) => [entry[0], i]));
    for (const e of this.enrollments.findGraded()) {
      const key = formatGrade(e.grade);
      if (index.has(key)) distribution[index.get(key)][1]++;
    }

    const schoolYear = this.enrollments.currentSchoolYear();
    const semester = this.enrollments.currentSemester();
    const current = this.enrollments.search({ schoolYear, semester }).filter((e) => e.status !== 'DROPPED');
    const load = new Map();
    for (const e of current) load.set(e.courseCode, (load.get(e.courseCode) || 0) + 1);
    const courseLoad = [...load.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));

    const top = [...gpas.entries()]
      .filter(([id]) => byId.has(id))
      .map(([id, g]) => ({ student: byId.get(id), gpa: g }))
      .sort((a, b) => a.gpa - b.gpa || sortableName(a.student).localeCompare(sortableName(b.student)))
      .slice(0, 5);

    const gpaValues = [...gpas.values()];
    return {
      totalStudents: students.length,
      activeStudents: students.filter((s) => s.status === 'ACTIVE').length,
      totalCourses: this.courses.count(),
      totalEnrollments: this.enrollments.count(),
      currentTermEnrollments: current.length,
      currentTerm: SEMESTER_SHORT[semester] + ' ' + schoolYear,
      averageGpa: average(gpaValues),
      deansListCount: gpaValues.filter((g) => g <= DEANS_LIST_GPA).length,
      gradeDistribution: distribution,
      studentsByProgram: this.students.countByProgram(),
      currentTermCourseLoad: courseLoad,
      topStudents: top,
    };
  }
}
