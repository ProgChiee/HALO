import { Routes, Route, Navigate } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';

import Login from '../pages/login/components/Login';
import Register from '../pages/login/components/Register';
import ForgotPassword from '../pages/login/components/ForgotPassword';
import Unauthorized from '../pages/login/components/Unauthorized';

import SuperAdminDashboard from '../pages/superadmin/components/SuperAdminDashboard';
import Admins from '../pages/superadmin/components/Admins';
import SuperAdminProfile from '../pages/superadmin/components/SuperAdminProfile';
import AdminDashboard from '../pages/admin/components/AdminDashboard';
import ProfessorManagement from '../pages/admin/components/ProfessorManagement';
import StudentManagement from '../pages/admin/components/StudentManagement';
import AdminProfile from '../pages/admin/components/AdminProfile';
import ProfessorDashboard from '../pages/professor/components/ProfessorDashboard';
import SubjectManagement from '../pages/professor/components/SubjectManagement';
import LessonEditor from '../pages/professor/components/LessonEditor';
import StudentProgress from '../pages/professor/components/StudentProgress';
import StudentDashboard from '../pages/student/components/StudentDashboard';
import Subjects from '../pages/student/components/Subjects';
import LessonChat from '../pages/student/components/LessonChat';
import Progress from '../pages/student/components/Progress';
import Badges from '../pages/student/components/Badges';
import Profile from '../pages/student/components/Profile';
import Quiz from '../pages/student/components/Quiz';

export default function AppRoutes() {
  return (
    <Routes>
      {/* Root path redirects straight to the login screen */}
      <Route path="/" element={<Navigate to="/login" replace />} />

      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/unauthorized" element={<Unauthorized />} />

      <Route
        path="/superadmin"
        element={
          <ProtectedRoute allowedRoles={['superadmin']}>
            <SuperAdminDashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/superadmin/admins"
        element={
          <ProtectedRoute allowedRoles={['superadmin']}>
            <Admins />
          </ProtectedRoute>
        }
      />

      <Route
        path="/superadmin/profile"
        element={
          <ProtectedRoute allowedRoles={['superadmin']}>
            <SuperAdminProfile />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin"
        element={
          <ProtectedRoute allowedRoles={['admin', 'superadmin']}>
            <AdminDashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/professors"
        element={
          <ProtectedRoute allowedRoles={['admin', 'superadmin']}>
            <ProfessorManagement />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/students"
        element={
          <ProtectedRoute allowedRoles={['admin', 'superadmin']}>
            <StudentManagement />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/profile"
        element={
          <ProtectedRoute allowedRoles={['admin', 'superadmin']}>
            <AdminProfile />
          </ProtectedRoute>
        }
      />

      <Route
        path="/professor"
        element={
          <ProtectedRoute allowedRoles={['professor']}>
            <ProfessorDashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/professor/subjects"
        element={
          <ProtectedRoute allowedRoles={['professor']}>
            <SubjectManagement />
          </ProtectedRoute>
        }
      />

      <Route
        path="/professor/subjects/:subjectId/week/:weekId"
        element={
          <ProtectedRoute allowedRoles={['professor']}>
            <LessonEditor />
          </ProtectedRoute>
        }
      />

      <Route
        path="/professor/progress"
        element={
          <ProtectedRoute allowedRoles={['professor']}>
            <StudentProgress />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student"
        element={
          <ProtectedRoute allowedRoles={['student']}>
            <StudentDashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/subjects"
        element={
          <ProtectedRoute allowedRoles={['student']}>
            <Subjects />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/lesson/:topicId/:weekId"
        element={
          <ProtectedRoute allowedRoles={['student']}>
            <LessonChat />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/progress"
        element={
          <ProtectedRoute allowedRoles={['student']}>
            <Progress />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/badges"
        element={
          <ProtectedRoute allowedRoles={['student']}>
            <Badges />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/profile"
        element={
          <ProtectedRoute allowedRoles={['student']}>
            <Profile />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/quiz/:topicId/:weekId"
        element={
          <ProtectedRoute allowedRoles={['student']}>
            <Quiz />
          </ProtectedRoute>
        }
      />

      {/* Catch-all: any unmatched path redirects to login */}
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}