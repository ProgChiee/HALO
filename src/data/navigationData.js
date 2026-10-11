// src/data/navigationData.js
import {
  LayoutGrid,
  BookOpen,
  TrendingUp,
  Award,
  User as UserIcon,
  Users,
  GraduationCap,
  Activity,
} from 'lucide-react';

export const STUDENT_NAV_ITEMS = [
  // `end: true` means this only counts as "active" on an EXACT path match.
  // Without it, "/student" would also match (and highlight) on "/student/subjects",
  // "/student/progress", etc. since NavLink does prefix matching by default.
  { label: 'Dashboard', icon: LayoutGrid, path: '/student', end: true, section: 'Main' },
  { label: 'Subjects', icon: BookOpen, path: '/student/subjects', section: 'Main' },
  { label: 'Progress', icon: TrendingUp, path: '/student/progress', section: 'Main' },
  { label: 'Badge', icon: Award, path: '/student/badges', section: 'Main' },
  { label: 'Profile', icon: UserIcon, path: '/student/profile', section: 'Account' },
];

export const SUPERADMIN_NAV_ITEMS = [
  { label: 'Dashboard', icon: LayoutGrid, path: '/superadmin', end: true, section: 'Main' },
  { label: 'Admins', icon: Users, path: '/superadmin/admins', section: 'Main' },
  { label: 'Monitoring', icon: Activity, path: '/superadmin/monitoring', section: 'Main' },
  { label: 'Profile', icon: UserIcon, path: '/superadmin/profile', section: 'Account' },
];

export const ADMIN_NAV_ITEMS = [
  { label: 'Dashboard', icon: LayoutGrid, path: '/admin', end: true, section: 'Main' },
  { label: 'Professor Management', icon: GraduationCap, path: '/admin/professors', section: 'Main' },
  { label: 'Student Management', icon: Users, path: '/admin/students', section: 'Main' },
  { label: 'Monitoring', icon: Activity, path: '/admin/monitoring', section: 'Main' },
  { label: 'Professor Mode', icon: GraduationCap, path: '/admin/professor-mode', section: 'Modes' },
  { label: 'Student Mode', icon: Users, path: '/admin/student-mode', section: 'Modes' },
  { label: 'Profile', icon: UserIcon, path: '/admin/profile', section: 'Account' },
];

// ✅ Professors now have their own subject/week management
// (ProfessorAcademicController), so "Subject Management" is back.
export const PROFESSOR_NAV_ITEMS = [
  { label: 'Dashboard', icon: LayoutGrid, path: '/professor', end: true, section: 'Main' },
  { label: 'Subjects', icon: BookOpen, path: '/professor/subjects', section: 'Main' },
  { label: 'Student Progress', icon: TrendingUp, path: '/professor/progress', section: 'Main' },
  { label: 'Profile', icon: UserIcon, path: '/professor/profile', section: 'Account' },
];