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
  { label: 'Dashboard', icon: LayoutGrid, path: '/student', end: true },
  { label: 'Subjects', icon: BookOpen, path: '/student/subjects' },
  { label: 'Progress', icon: TrendingUp, path: '/student/progress' },
  { label: 'Badge', icon: Award, path: '/student/badges' },
  { label: 'Profile', icon: UserIcon, path: '/student/profile' },
];

export const SUPERADMIN_NAV_ITEMS = [
  { label: 'Dashboard', icon: LayoutGrid, path: '/superadmin', end: true },
  { label: 'Admins', icon: Users, path: '/superadmin/admins' },
  { label: 'Profile', icon: UserIcon, path: '/superadmin/profile' },
];

export const ADMIN_NAV_ITEMS = [
  { label: 'Dashboard', icon: LayoutGrid, path: '/admin', end: true },
  { label: 'Professor Management', icon: GraduationCap, path: '/admin/professors' },
  { label: 'Student Management', icon: Users, path: '/admin/students' },
  { label: 'Monitoring', icon: Activity, path: '/admin/monitoring' },
  { label: 'Profile', icon: UserIcon, path: '/admin/profile' },
];

export const PROFESSOR_NAV_ITEMS = [
  { label: 'Dashboard', icon: LayoutGrid, path: '/professor', end: true },
  { label: 'Subject Management', icon: BookOpen, path: '/professor/subjects' },
  { label: 'Student Progress', icon: TrendingUp, path: '/professor/progress' },
  { label: 'Profile', icon: UserIcon, path: '/professor/profile' },
];