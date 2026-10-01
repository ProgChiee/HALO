import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/login/useAuth';

/**
 * Usage:
 * <Route element={<ProtectedRoute allowedRoles={['admin', 'superadmin']} />}>
 *   <Route path="/admin" element={<AdminDashboard />} />
 * </Route>
 *
 * If no allowedRoles is passed, any logged-in user can access it.
 */
export default function ProtectedRoute({ allowedRoles, children }) {
  const { isAuthenticated, role, isLoading } = useAuth();

  if (isLoading) {
    return <div>Loading...</div>; // swap with a proper loading spinner component
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(role)) {
    return <Navigate to="/unauthorized" replace />;
  }

  return children;
}