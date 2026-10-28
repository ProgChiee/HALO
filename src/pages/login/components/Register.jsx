import { useRef, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Eye, EyeOff } from 'lucide-react';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { register as registerService } from '../../../services/authService';
import bgImage from '../../../assets/login/Background.png';
import { validateRegistration, registrationHints, registrationFailure } from '../../../utils/registration';
import styles from '../styles/Register.module.css';


// Matches the backend's YearLevel enum exactly.
const YEAR_LEVELS = [
  { value: 'FIRST_YEAR', label: '1st Year' },
  { value: 'SECOND_YEAR', label: '2nd Year' },
];

export default function Register() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [studentId, setStudentId] = useState('');
  const [section, setSection] = useState('');
  const [yearLevel, setYearLevel] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errors, setErrors] = useState({});
  const submitting = useRef(false);
  const { showToast } = useToast();
  const navigate = useNavigate();

  function clearField(field) { setErrors(current => { const next = { ...current }; delete next[field]; return next; }); }

  async function handleSubmit(e) {
    e.preventDefault();
    if (submitting.current) return;
    setError('');
    const result = validateRegistration({ name: fullName, email, password, confirmPassword, studentId, section, yearLevel });
    setErrors(result.errors);
    if (Object.keys(result.errors).length) {
      requestAnimationFrame(() => document.getElementById('register-' + Object.keys(result.errors)[0])?.focus());
      return;
    }
    submitting.current = true;
    setIsLoading(true);

    try {
      await registerService(result.value.name, result.value.email, password, result.value.studentId, result.value.section, yearLevel);
      showToast('Account created! Please sign in.', 'success');
      navigate('/login');
    } catch (failure) {
      const result = registrationFailure(failure);
      setError(result.message); setErrors(result.errors);
      showToast(result.message, 'error');
    } finally {
      submitting.current = false;
      setIsLoading(false);
    }
  }

  return (
    <main className={styles.wrapper} style={{ '--bg-image': `url(${bgImage})` }}>
      <section className={styles.card} aria-labelledby="register-title">
        <div className={styles.formCard}>
          <div className={styles.formHeader}>
        <button className={styles.backBtn} onClick={() => navigate(-1)}>
          <ArrowLeft size={16} />
          Back
        </button>

        <h1 id="register-title" className={styles.title}>Create your account</h1>
        <p className={styles.subtitle}>
          Start learning with HALO.
        </p>

        </div>
          <form noValidate onSubmit={handleSubmit} className={styles.form}>
<fieldset className={styles.group}><legend className={styles.groupTitle}>Personal information</legend>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-name">Full name</label>
              <div className={styles.inputWrapper}>
                <input
                  type="text"
                  className={styles.input}
                  id="register-name" autoComplete="name" aria-invalid={!!errors.name} aria-describedby="register-name-hint" disabled={isLoading}
                  value={fullName}
                  onChange={(e) => { setFullName(e.target.value); clearField('name'); }}
                  placeholder="Your full name"
                />
              </div>
              <p id="register-name-hint" className={errors.name ? styles.fieldError : styles.hint}>{errors.name || registrationHints.name}</p>
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-email">Email</label>
              <div className={styles.inputWrapper}>
                <input
                  type="email"
                  className={styles.input}
                  id="register-email" autoComplete="email" aria-invalid={!!errors.email} aria-describedby="register-email-hint" disabled={isLoading}
                  value={email}
                  onChange={(e) => { setEmail(e.target.value); clearField('email'); }}
                  placeholder="Your@Paterostechnologicalcollege.edu.ph"
                />
              </div>
              <p id="register-email-hint" className={errors.email ? styles.fieldError : styles.hint}>{errors.email || registrationHints.email}</p>
            </div>

            </fieldset>
<fieldset className={styles.group}><legend className={styles.groupTitle}>School information</legend>
<div className={styles.field}>
              <label className={styles.label} htmlFor="register-studentId">Student ID</label>
              <div className={styles.inputWrapper}>
                <input
                  type="text"
                  className={styles.input}
                  id="register-studentId" aria-invalid={!!errors.studentId} aria-describedby="register-studentId-hint" disabled={isLoading}
                  value={studentId}
                  onChange={(e) => { setStudentId(e.target.value); clearField('studentId'); }}
                  placeholder="e.g. 29AHRT-0168"
                />
              </div>
              <p id="register-studentId-hint" className={errors.studentId ? styles.fieldError : styles.hint}>{errors.studentId || registrationHints.studentId}</p>
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-section">Section</label>
              <div className={styles.inputWrapper}>
                <input
                  type="text"
                  className={styles.input}
                  id="register-section" aria-invalid={!!errors.section} aria-describedby="register-section-hint" disabled={isLoading}
                  value={section}
                  onChange={(e) => { setSection(e.target.value); clearField('section'); }}
                  placeholder="e.g. BSHM 1A"
                />
              </div>
              <p id="register-section-hint" className={errors.section ? styles.fieldError : styles.hint}>{errors.section || registrationHints.section}</p>
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-yearLevel">Year level</label>
              <select className={styles.input} id="register-yearLevel" aria-invalid={!!errors.yearLevel} aria-describedby="register-yearLevel-hint" disabled={isLoading}
                  value={yearLevel} onChange={(e) => { setYearLevel(e.target.value); clearField('yearLevel'); }}>
                <option value="">Select year level</option>
                {YEAR_LEVELS.map((yl) => (
                  <option key={yl.value} value={yl.value}>{yl.label}</option>
                ))}
              </select>
              <p id="register-yearLevel-hint" className={errors.yearLevel ? styles.fieldError : styles.hint}>{errors.yearLevel || registrationHints.yearLevel}</p>
            </div>

            </fieldset>
<fieldset className={styles.group}><legend className={styles.groupTitle}>Account information</legend>
<div className={styles.field}>
              <label className={styles.label} htmlFor="register-password">Password</label>
              <div className={styles.inputWrapper}>
                <input
                  type={showPassword ? 'text' : 'password'}
                  className={styles.input}
                  id="register-password" autoComplete="new-password" aria-invalid={!!errors.password} aria-describedby="register-password-hint" disabled={isLoading}
                  value={password}
                  onChange={(e) => { setPassword(e.target.value); clearField('password'); }}
                  placeholder="Password"
                />
                <button
                  type="button"
                  className={styles.eyeToggle}
                  onClick={() => setShowPassword((v) => !v)}
                  aria-controls="register-password" aria-pressed={showPassword} disabled={isLoading}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              <p id="register-password-hint" className={errors.password ? styles.fieldError : styles.hint}>{errors.password || registrationHints.password}</p>
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="register-confirmPassword">Confirm password</label>
              <div className={styles.inputWrapper}>
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  className={styles.input}
                  id="register-confirmPassword" autoComplete="new-password" aria-invalid={!!errors.confirmPassword} aria-describedby="register-confirmPassword-hint" disabled={isLoading}
                  value={confirmPassword}
                  onChange={(e) => { setConfirmPassword(e.target.value); clearField('confirmPassword'); }}
                  placeholder="Repeat your password"
                />
                <button
                  type="button"
                  className={styles.eyeToggle}
                  onClick={() => setShowConfirmPassword((v) => !v)}
                  aria-controls="register-confirmPassword" aria-pressed={showConfirmPassword} disabled={isLoading}
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                >
                  {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              <p id="register-confirmPassword-hint" className={errors.confirmPassword ? styles.fieldError : styles.hint}>{errors.confirmPassword || registrationHints.confirmPassword}</p>
            </div>

            </fieldset>
            {error && <p role="alert" className={styles.error}>{error}</p>}

            <Button type="submit" disabled={isLoading} isLoading={isLoading}>Create Account</Button>

            <p className={styles.signinText}>
              Already have an account?{' '}
              <Link to="/login" className={styles.signinLink}>Sign in</Link>
            </p>
          </form>
        </div>
      </section>
    </main>
  );
}