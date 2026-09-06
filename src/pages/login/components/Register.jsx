import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Mail, KeyRound, Eye, EyeOff } from 'lucide-react';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/ToastContext';
import {
  sendPasswordResetCode,
  resendPasswordResetCode,
  resetPassword,
} from '../../../services/authService';
import bgImage from '../../../assets/login/BG.jpeg';
import styles from '../styles/ForgotPassword.module.css';

// Change this if your institutional domain is different
const INSTITUTIONAL_DOMAIN = '@paterostechnologicalcollege.edu.ph';
const RESEND_COOLDOWN_SECONDS = 30;

export default function ForgotPassword() {
  const [step, setStep] = useState(1); // 1 = enter email, 2 = enter code + new password

  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const [error, setError] = useState('');
  const [showEmailModal, setShowEmailModal] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);

  const { showToast } = useToast();
  const navigate = useNavigate();

  // Countdown ticker for the resend cooldown
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setInterval(() => {
      setResendCooldown((prev) => prev - 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [resendCooldown]);

  async function handleSendCode(e) {
    e.preventDefault();
    setError('');

    if (!email) {
      setError('Please enter your email.');
      return;
    }

    if (!email.toLowerCase().endsWith(INSTITUTIONAL_DOMAIN)) {
      setShowEmailModal(true);
      return;
    }

    setIsLoading(true);

    try {
      await sendPasswordResetCode(email);
      showToast('Verification code sent to your email.', 'success');
      setStep(2);
      setResendCooldown(RESEND_COOLDOWN_SECONDS);
    } catch (err) {
      console.error(err);
      setError('Something went wrong. Please try again.');
    } finally {
      setIsLoading(false);
    }
  }

  async function handleResend() {
    if (resendCooldown > 0 || isResending) return;

    setIsResending(true);
    try {
      await resendPasswordResetCode(email);
      showToast('Verification code resent.', 'success');
      setResendCooldown(RESEND_COOLDOWN_SECONDS);
    } catch (err) {
      console.error(err);
      showToast("Couldn't resend the code. Please try again.", 'error');
    } finally {
      setIsResending(false);
    }
  }

  async function handleResetPassword(e) {
    e.preventDefault();
    setError('');

    if (!code || !newPassword || !confirmPassword) {
      setError('Please fill in all fields.');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }

    setIsLoading(true);

    try {
      await resetPassword(email, code, newPassword);
      showToast('Password reset successfully. Please sign in.', 'success');
      navigate('/login');
    } catch (err) {
      console.error(err);
      setError('Something went wrong. Please try again.');
    } finally {
      setIsLoading(false);
    }
  }

  function handleBack() {
    if (step === 2) {
      setStep(1);
      setError('');
    } else {
      navigate('/login');
    }
  }

  return (
    <div className={styles.wrapper} style={{ '--bg-image': `url(${bgImage})` }}>
      <div className={styles.card}>
        <button className={styles.backBtn} onClick={handleBack}>
          <ArrowLeft size={16} />
          Back
        </button>

        <div className={styles.iconBadge}>
          <Mail size={18} />
        </div>

        <h1 className={styles.title}>Forgot Password?</h1>

        {step === 1 ? (
          <>
            <p className={styles.subtitle}>
              Enter your email and we'll send you a code to reset your password.
            </p>

            <div className={styles.formCard}>
              <form onSubmit={handleSendCode} className={styles.form}>
                <div className={styles.field}>
                  <label className={styles.label}>Email Address</label>
                  <div className={styles.inputWrapper}>
                    <Mail size={18} className={styles.inputIcon} />
                    <input
                      type="email"
                      className={styles.input}
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder={`Your@${INSTITUTIONAL_DOMAIN.slice(1)}`}
                    />
                  </div>
                </div>

                {error && <p className={styles.error}>{error}</p>}

                <Button type="submit" isLoading={isLoading}>Send Code</Button>

                <p className={styles.signinText}>
                  Remember your password?{' '}
                  <Link to="/login" className={styles.signinLink}>Sign in</Link>
                </p>
              </form>
            </div>
          </>
        ) : (
          <>
            <p className={styles.subtitle}>
              Enter the verification code we sent to your email, then set your new password.
            </p>

            <div className={styles.formCard}>
              <form onSubmit={handleResetPassword} className={styles.form}>
                <div className={styles.field}>
                  <label className={styles.label}>Enter verification code</label>
                  <div className={styles.inputWrapper}>
                    <Mail size={18} className={styles.inputIcon} />
                    <input
                      type="text"
                      className={styles.input}
                      value={code}
                      onChange={(e) => setCode(e.target.value)}
                      placeholder="Enter verification code"
                    />
                  </div>
                  <button
                    type="button"
                    className={styles.resendBtn}
                    onClick={handleResend}
                    disabled={resendCooldown > 0 || isResending}
                  >
                    {resendCooldown > 0
                      ? `Resend code in ${resendCooldown}s`
                      : isResending
                        ? 'Resending...'
                        : 'Resend code'}
                  </button>
                </div>

                <div className={styles.field}>
                  <label className={styles.label}>New password</label>
                  <div className={styles.inputWrapper}>
                    <KeyRound size={18} className={styles.inputIcon} />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      className={styles.input}
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
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
                  <label className={styles.label}>Password</label>
                  <div className={styles.inputWrapper}>
                    <KeyRound size={18} className={styles.inputIcon} />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      className={styles.input}
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="Confirm new password"
                    />
                  </div>
                </div>
                      
                {error && <p className={styles.error}>{error}</p>}

                <Button type="submit" isLoading={isLoading}>Reset Password</Button>
              </form>
            </div>
          </>
        )}
      </div>

      {/* Institutional email popup */}
      {showEmailModal && (
        <div className={styles.modalOverlay} onClick={() => setShowEmailModal(false)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <h3 className={styles.modalTitle}>Institutional email required</h3>
            <p className={styles.modalText}>
              Please use your school email address (ending in {INSTITUTIONAL_DOMAIN}) to reset your password.
            </p>
            <Button onClick={() => setShowEmailModal(false)}>Okay</Button>
          </div>
        </div>
      )}
    </div>
  );
}