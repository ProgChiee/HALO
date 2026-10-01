import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate, useParams } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import { ROLES } from '../utils/roles';

const Login = lazy(() => import('../pages/login/components/Login'));
const Register = lazy(() => import('../pages/login/components/Register'));
const ForgotPassword = lazy(() => import('../pages/login/components/ForgotPassword'));
const Unauthorized = lazy(() => import('../pages/login/components/Unauthorized'));

const SuperAdminDashboard = lazy(() => import('../pages/superadmin/components/SuperAdminDashboard'));
const Admins = lazy(() => import('../pages/superadmin/components/Admins'));
const SuperAdminProfile = lazy(() => import('../pages/superadmin/components/SuperAdminProfile'));
const AdminDashboard = lazy(() => import('../pages/admin/components/AdminDashboard'));
const ProfessorManagement = lazy(() => import('../pages/admin/components/ProfessorManagement'));
const StudentManagement = lazy(() => import('../pages/admin/components/StudentManagement'));
const AdminProfile = lazy(() => import('../pages/admin/components/AdminProfile'));
const Monitoring = lazy(() => import('../pages/admin/components/Monitoring'));
const ProfessorDashboard = lazy(() => import('../pages/professor/components/ProfessorDashboard'));
const SubjectManagement = lazy(() => import('../pages/admin/components/SubjectManagement'));
const ProfessorSubjectManagement = lazy(() => import('../pages/professor/components/SubjectManagement'));
const LessonEditor = lazy(() => import('../pages/professor/components/LessonEditor'));
const StudentProgress = lazy(() => import('../pages/professor/components/StudentProgress'));
const ProfessorProfile = lazy(() => import('../pages/professor/components/ProfessorProfile'));
const StudentDashboard = lazy(() => import('../pages/student/components/StudentDashboard'));
const Subjects = lazy(() => import('../pages/student/components/Subjects'));
const LessonChat = lazy(() => import('../pages/student/components/LessonChat'));
const Progress = lazy(() => import('../pages/student/components/Progress'));
const Badges = lazy(() => import('../pages/student/components/Badges'));
const Profile = lazy(() => import('../pages/student/components/Profile'));
const Quiz = lazy(() => import('../pages/student/components/Quiz'));

function LessonChatRoute() {
  const { topicId, weekId } = useParams();
  return <LessonChat key={topicId + ':' + weekId} />;
}
function QuizRoute() {
  const { topicId, weekId } = useParams();
  return <Quiz key={topicId + ':' + weekId} />;
}
function LessonEditorRoute() {
  const { subjectId, weekId } = useParams();
  return <LessonEditor key={subjectId + ':' + weekId} />;
}

export default function AppRoutes() {
  return (
    <Suspense fallback={<p role="status">Loading page...</p>}>
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
          <ProtectedRoute allowedRoles={[ROLES.SUPERADMIN]}>
            <SuperAdminDashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/superadmin/admins"
        element={
          <ProtectedRoute allowedRoles={[ROLES.SUPERADMIN]}>
            <Admins />
          </ProtectedRoute>
        }
      />

      <Route
        path="/superadmin/profile"
        element={
          <ProtectedRoute allowedRoles={[ROLES.SUPERADMIN]}>
            <SuperAdminProfile />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin"
        element={
          <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
            <AdminDashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/professors"
        element={
          <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
            <ProfessorManagement />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/students"
        element={
          <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
            <StudentManagement />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/monitoring"
        element={
          <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
            <Monitoring />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/profile"
        element={
          <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
            <AdminProfile />
          </ProtectedRoute>
        }
      />

      <Route
        path="/professor"
        element={
          <ProtectedRoute allowedRoles={[ROLES.PROFESSOR]}>
            <ProfessorDashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/subjects"
        element={
          <ProtectedRoute allowedRoles={[ROLES.ADMIN]}>
            <SubjectManagement />
          </ProtectedRoute>
        }
      />

      <Route
        path="/professor/subjects"
        element={
          <ProtectedRoute allowedRoles={[ROLES.PROFESSOR]}>
            <ProfessorSubjectManagement />
          </ProtectedRoute>
        }
      />

      <Route
        path="/professor/subjects/:subjectId/week/:weekId"
        element={
          <ProtectedRoute allowedRoles={[ROLES.PROFESSOR]}>
            <LessonEditorRoute />
          </ProtectedRoute>
        }
      />

      <Route
        path="/professor/progress"
        element={
          <ProtectedRoute allowedRoles={[ROLES.PROFESSOR]}>
            <StudentProgress />
          </ProtectedRoute>
        }
      />

      <Route
        path="/professor/profile"
        element={
          <ProtectedRoute allowedRoles={[ROLES.PROFESSOR]}>
            <ProfessorProfile />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student"
        element={
          <ProtectedRoute allowedRoles={[ROLES.STUDENT]}>
            <StudentDashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/subjects"
        element={
          <ProtectedRoute allowedRoles={[ROLES.STUDENT]}>
            <Subjects />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/lesson/:topicId/:weekId"
        element={
          <ProtectedRoute allowedRoles={[ROLES.STUDENT]}>
            <LessonChatRoute />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/progress"
        element={
          <ProtectedRoute allowedRoles={[ROLES.STUDENT]}>
            <Progress />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/badges"
        element={
          <ProtectedRoute allowedRoles={[ROLES.STUDENT]}>
            <Badges />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/profile"
        element={
          <ProtectedRoute allowedRoles={[ROLES.STUDENT]}>
            <Profile />
          </ProtectedRoute>
        }
      />

      <Route
        path="/student/quiz/:topicId/:weekId"
        element={
          <ProtectedRoute allowedRoles={[ROLES.STUDENT]}>
            <QuizRoute />
          </ProtectedRoute>
        }
      />

      <Route path="/superadmin/monitoring" element={
        <ProtectedRoute allowedRoles={[ROLES.SUPERADMIN]}>
          <Monitoring />
        </ProtectedRoute>
      } />

      {/* Catch-all: any unmatched path redirects to login */}
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
    </Suspense>
  );
}