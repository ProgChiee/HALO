import { apiErrorMessage } from '../../../utils/apiErrors';
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Mail, KeyRound, Eye, EyeOff } from 'lucide-react';
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
      setError(err.response?.status === 401 || err.response?.status === 403
        ? 'Login failed. Check your credentials and account status.'
        : apiErrorMessage(err, 'Unable to sign in. Check that the backend is available and try again.'));
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className={styles.wrapper} style={{ '--bg-image': `url(${bgImage})` }}>
      <div className={styles.card}>
        <img src={logo} alt="HALO logo" className={styles.logo} />
      

        <div className={styles.formCard}>
          <h2 className={styles.welcome}>Welcome back</h2>
          <p className={styles.subtitle}>Sign in to continue your journey</p>

          <form onSubmit={handleSubmit} className={styles.form}>
            <div className={styles.inputWrapper}>
              <Mail size={18} className={styles.inputIcon} />
              <input
                type="email"
                className={styles.input}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Email Address"
              />
            </div>

            <div className={styles.inputWrapper}>
              <KeyRound size={18} className={styles.inputIcon} />
              <input
                type={showPassword ? 'text' : 'password'}
                className={styles.input}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Password"
              />
              <button
                type="button"
                className={styles.eyeToggle}
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
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

            {error && <p className={styles.error}>{error}</p>}

            <Button type="submit" isLoading={isLoading}>Login</Button>

            <p className={styles.signupText}>
              Don't have an account?{' '}
              <Link to="/register" className={styles.signupLink}>Sign up</Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}
