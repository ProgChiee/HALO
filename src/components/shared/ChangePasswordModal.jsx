import { useState } from 'react';
import { X } from 'lucide-react';
import Button from './Button';
import { useToast } from '../../context/notifications/ToastContext';
import { changePassword } from '../../services/authService';
import styles from './ChangePasswordModal.module.css';

/**
 * Shared "Change Password" modal — used by every role's Profile page
 * (Student, Professor, Admin, Superadmin), so the form/validation/API
 * call only needs to exist in one place.
 *
 * Usage:
 * const [showChangePassword, setShowChangePassword] = useState(false);
 * <ChangePasswordModal isOpen={showChangePassword} onClose={() => setShowChangePassword(false)} />
 */
export default function ChangePasswordModal({ isOpen, onClose }) {
  const { showToast } = useToast();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmNewPassword, setConfirmNewPassword] = useState('');
  const [formError, setFormError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  if (!isOpen) return null;

  function resetAndClose() {
    setCurrentPassword('');
    setNewPassword('');
    setConfirmNewPassword('');
    setFormError('');
    onClose();
  }

  // Used by the X button and clicking the overlay — blocks closing while
  // a request is in flight (matches the Cancel button's disabled state).
  // The success path inside handleSubmit calls resetAndClose() directly
  // instead, so a successful change always closes the modal even though
  // isSaving is still true at that point (it's cleared in `finally`,
  // which runs after resetAndClose() there).
  function handleCloseAttempt() {
    if (isSaving) return;
    resetAndClose();
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (isSaving) return;
    setFormError('');

    if (!currentPassword || !newPassword || !confirmNewPassword) {
      setFormError('Please fill in all fields.');
      return;
    }

    if (newPassword.length < 8) {
      setFormError('New password must be at least 8 characters.');
      return;
    }

    if (newPassword !== confirmNewPassword) {
      setFormError('New passwords do not match.');
      return;
    }

    if (newPassword === currentPassword) {
      setFormError('New password must be different from your current password.');
      return;
    }

    setIsSaving(true);
    try {
      await changePassword(currentPassword, newPassword);
      showToast('Password changed successfully.', 'success');
      resetAndClose();
    } catch (err) {
      console.error(err);
      setFormError('Something went wrong. Please check your current password and try again.');
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className={styles.modalOverlay} onClick={handleCloseAttempt}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.modalHeader}>
          <h3 className={styles.modalTitle}>Change Password</h3>
          <button className={styles.closeBtn} onClick={handleCloseAttempt} aria-label="Close">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.field}>
            <label className={styles.label}>Current password</label>
            <input
              type="password"
              className={styles.input}
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              autoComplete="current-password"
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label}>New password</label>
            <input
              type="password"
              className={styles.input}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label}>Confirm new password</label>
            <input
              type="password"
              className={styles.input}
              value={confirmNewPassword}
              onChange={(e) => setConfirmNewPassword(e.target.value)}
              autoComplete="new-password"
            />
          </div>

          {formError && <p className={styles.formError}>{formError}</p>}

          <div className={styles.actions}>
            <Button type="button" variant="secondary" onClick={handleCloseAttempt} disabled={isSaving}>
              Cancel
            </Button>
            <Button type="submit" isLoading={isSaving}>
              Save Changes
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}