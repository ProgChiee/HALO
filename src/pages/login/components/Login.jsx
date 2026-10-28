import { apiErrorMessage } from '../../../utils/apiErrors';
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Eye, EyeOff } from 'lucide-react';
import { useAuth } from '../../../context/login/useAuth';
import { useToast } from '../../../context/notifications/useToast';
import { login as loginService } from '../../../services/authService';
import { parseLoginResponse } from '../../../utils/backendContract';
import { ROLE_HOME, getPasswordChangeRoute } from '../../../utils/roles';
import Button from '../../../components/shared/Button';
import bgImage from '../../../assets/login/Background.png';
import logo from '../../../assets/login/Logo.png';
import styles from '../styles/Login.module.css';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  async function handleSubmit(e) {
    e.preventDefault();
    if (isLoading) return;
    setError('');

    if (!email || !password) {
      setError('Please enter both email and password.');
      return;
    }

    setIsLoading(true);

    try {
      const response = await loginService(email.trim(), password);
      const { user, token } = parseLoginResponse(response);
      login(user, token, rememberMe);
      showToast('Welcome back!', 'success');
      navigate(user.mustChangePassword ? getPasswordChangeRoute(user.role) : ROLE_HOME[user.role], { replace: true });
    } catch (err) {
      setError(['ACCOUNT_INACTIVE', 'ACCOUNT_ARCHIVED'].includes(err.response?.data?.code) ? apiErrorMessage(err) : err.response?.status === 401 || err.response?.status === 403
        ? 'Login failed. Check your credentials and account status.'
        : apiErrorMessage(err, 'Unable to sign in. Check that the backend is available and try again.'));
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <main className={styles.wrapper} style={{ '--bg-image': `url(${bgImage})` }}>
      <section className={styles.card} aria-labelledby="login-title">
        <img src={logo} alt="HALO logo" className={styles.logo} />
      

        <div className={styles.formCard}>
          <h1 id="login-title" className={styles.welcome}>Welcome back</h1>
          <p className={styles.subtitle}>Sign in to continue learning with HALO.</p>

          <form onSubmit={handleSubmit} className={styles.form}>
            <div className={styles.field}>
              <label htmlFor="login-email" className={styles.label}>Email</label>
              <input
                id="login-email" name="email" autoComplete="username" disabled={isLoading}
                type="email"
                className={styles.input}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Enter your email address"
              />
            </div>

            <div className={styles.field}>
              <label htmlFor="login-password" className={styles.label}>Password</label>
              <div className={styles.inputWrapper}>
              <input
                id="login-password" name="password" autoComplete="current-password" disabled={isLoading}
                type={showPassword ? 'text' : 'password'}
                className={styles.input}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter your password"
              />
              <button
                type="button"
                className={styles.eyeToggle}
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                aria-controls="login-password" aria-pressed={showPassword} disabled={isLoading}
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
              </div>
            </div>

            <div className={styles.rememberRow}>
              <label className={styles.checkboxLabel}>
                <input
                  type="checkbox"
                  className={styles.checkbox}
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                />
                Remember me
              </label>

              <Link to="/forgot-password" className={styles.forgotLink}>
                Forgot password?
              </Link>
            </div>

            {error && <p role="alert" className={styles.error}>{error}</p>}

            <Button type="submit" isLoading={isLoading}>Sign In</Button>

            <p className={styles.signupText}>
              Don't have an account?{' '}
              <Link to="/register" className={styles.signupLink}>Sign up</Link>
            </p>
          </form>
        </div>
      </section>
    </main>
  );
}
