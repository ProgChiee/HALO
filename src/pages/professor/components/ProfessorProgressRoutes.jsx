import { Routes, Route, Navigate } from 'react-router-dom';
import StudentProgress from './StudentProgress';
import LearningEngagementPage from './LearningEngagementPage';

export default function ProfessorProgressRoutes() {
  return <Routes>
    <Route index element={<Navigate to="account-status" replace />} />
    <Route path="account-status" element={<StudentProgress />} />
    <Route path="learning-engagement" element={<LearningEngagementPage />} />
    <Route path="*" element={<Navigate to="../account-status" replace />} />
  </Routes>;
}
