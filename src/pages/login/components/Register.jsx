import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, User, Mail, KeyRound, Eye, EyeOff, Hash, Users2 } from 'lucide-react';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { register as registerService } from '../../../services/authService';
import bgImage from '../../../assets/login/Background.png';
import styles from '../styles/Register.module.css';

// Change this if your institutional domain is different
const INSTITUTIONAL_DOMAIN = '@paterostechnologicalcollege.edu.ph';

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
  const [yearLevel, setYearLevel] = useState('FIRST_YEAR');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showEmailModal, setShowEmailModal] = useState(false);
  const { showToast } = useToast();
  const navigate = useNavigate();

  async function handleSubmit(e) {
    e.preventDefault();
    if (isLoading) return;
    setError('');

    if (!fullName || !email || !password || !confirmPassword || !studentId || !section) {
      setError('Please fill in all fields.');
      return;
    }

    if (!email.toLowerCase().endsWith(INSTITUTIONAL_DOMAIN)) {
      setShowEmailModal(true);
      return;
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }

    setIsLoading(true);

    try {
      await registerService(fullName, email, password, studentId, section, yearLevel);
      showToast('Account created! Please sign in.', 'success');
      navigate('/login');
    } catch {
      setError('Something went wrong. Please try again.');
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className={styles.wrapper} style={{ '--bg-image': `url(${bgImage})` }}>
      <div className={styles.card}>
        <div className={styles.formCard}>
          <div className={styles.formHeader}>
        <button className={styles.backBtn} onClick={() => navigate(-1)}>
          <ArrowLeft size={16} />
          Back
        </button>

        <h1 className={styles.title}>Register account</h1>
        <p className={styles.subtitle}>
          Create your HALO account to start your learning journey.
        </p>

        </div>
          <form onSubmit={handleSubmit} className={styles.form}>
<fieldset className={styles.group}><legend className={styles.groupTitle}>Personal information</legend>
            <div className={styles.field}>
              <label className={styles.label}>Fullname</label>
              <div className={styles.inputWrapper}>
                <User size={18} className={styles.inputIcon} />
                <input
                  type="text"
                  className={styles.input}
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="Your full name"
                />
              </div>
            </div>

            <div className={styles.field}>
              <label className={styles.label}>Email</label>
              <div className={styles.inputWrapper}>
                <Mail size={18} className={styles.inputIcon} />
                <input
                  type="email"
                  className={styles.input}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="Your@Paterostechnologicalcollege.edu.ph"
                />
              </div>
            </div>

            </fieldset>
<fieldset className={styles.group}><legend className={styles.groupTitle}>School information</legend>
<div className={styles.field}>
              <label className={styles.label}>Student ID</label>
              <div className={styles.inputWrapper}>
                <Hash size={18} className={styles.inputIcon} />
                <input
                  type="text"
                  className={styles.input}
                  value={studentId}
                  onChange={(e) => setStudentId(e.target.value)}
                  placeholder="e.g. 2023-00123"
                />
              </div>
            </div>

            <div className={styles.field}>
              <label className={styles.label}>Section</label>
              <div className={styles.inputWrapper}>
                <Users2 size={18} className={styles.inputIcon} />
                <input
                  type="text"
                  className={styles.input}
                  value={section}
                  onChange={(e) => setSection(e.target.value)}
                  placeholder="e.g. BSHM 1A"
                />
              </div>
            </div>

            <div className={styles.field}>
              <label className={styles.label}>Year level</label>
              <select className={styles.input} value={yearLevel} onChange={(e) => setYearLevel(e.target.value)}>
                {YEAR_LEVELS.map((yl) => (
                  <option key={yl.value} value={yl.value}>{yl.label}</option>
                ))}
              </select>
            </div>

            </fieldset>
<fieldset className={styles.group}><legend className={styles.groupTitle}>Account information</legend>
<div className={styles.field}>
              <label className={styles.label}>Password</label>
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
            </div>

            <div className={styles.field}>
              <label className={styles.label}>Confirm password</label>
              <div className={styles.inputWrapper}>
                <KeyRound size={18} className={styles.inputIcon} />
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  className={styles.input}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Repeat your password"
                />
                <button
                  type="button"
                  className={styles.eyeToggle}
                  onClick={() => setShowConfirmPassword((v) => !v)}
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                >
                  {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            </fieldset>
            {error && <p className={styles.error}>{error}</p>}

            <Button type="submit" isLoading={isLoading}>Create account</Button>

            <p className={styles.signinText}>
              Already have an account?{' '}
              <Link to="/login" className={styles.signinLink}>Log in</Link>
            </p>
          </form>
        </div>
      </div>

      {/* Institutional email popup */}
      {showEmailModal && (
        <div className={styles.modalOverlay} onClick={() => setShowEmailModal(false)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <h3 className={styles.modalTitle}>Institutional email required</h3>
            <p className={styles.modalText}>
              Please use your school email address (ending in {INSTITUTIONAL_DOMAIN}) to create your account.
            </p>
            <Button onClick={() => setShowEmailModal(false)}>Okay</Button>
          </div>
        </div>
      )}
    </div>
  );
}