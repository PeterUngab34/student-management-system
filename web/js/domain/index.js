// Composition root for the services (AppContext in Java).
import { StudentService } from './students.js';
import { CourseService } from './courses.js';
import { EnrollmentService } from './enrollments.js';
import { DashboardService } from './dashboard.js';

export function createServices(db, clock = () => new Date()) {
  const students = new StudentService(db, clock);
  const courses = new CourseService(db);
  const enrollments = new EnrollmentService(db, students, courses, clock);
  const dashboard = new DashboardService(students, courses, enrollments);
  return { db, students, courses, enrollments, dashboard };
}
