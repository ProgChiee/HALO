import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/login/AuthContext';
import { ToastProvider } from './context/notifications/ToastContext';
import AccountTheme from './components/AccountTheme';
import AppRoutes from './routes/AppRoutes';

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <AccountTheme />
          <AppRoutes />
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}