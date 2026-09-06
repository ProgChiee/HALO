import { useState, useEffect } from 'react';
import { User as UserIcon, KeyRound, X } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import ChangePasswordModal from '../../../components/shared/ChangePasswordModal';
import { useToast } from '../../../context/notifications/ToastContext';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { getProfessorProfile, updateProfessorProfile } from '../../../services/professor/professorService';
import styles from '../styles/ProfessorProfile.module.css';

export default function ProfessorProfile() {
  const { showToast } = useToast();
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [profile, setProfile] = useState(null);

  const [showEditModal, setShowEditModal] = useState(false);
  const [showChangePassword, setShowChangePassword] = useState(false);
  const [professorId, setProfessorId] = useState('');
  const [formError, setFormError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        const data = await getProfessorProfile();
        if (isMounted) setProfile(data);
      } catch (err) {
        console.error(err);
        if (isMounted) setLoadError(true);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }
    loadData();
    return () => { isMounted = false; };
  }, []);

  function openEditModal() {
    setProfessorId(profile.professorId);
    setFormError('');
    setShowEditModal(true);
  }

  async function handleSaveEdit(e) {
    e.preventDefault();
    if (isSaving) return;
    setFormError('');

    if (!professorId.trim()) {
      setFormError('Please enter a Professor ID.');
      return;
    }

    setIsSaving(true);
    try {
      const updated = await updateProfessorProfile({ professorId: professorId.trim() });
      setProfile(updated);
      showToast('Professor ID updated.', 'success');
      setShowEditModal(false);
    } catch (err) {
      console.error(err);
      setFormError('Something went wrong. Please try again.');
    } finally {
      setIsSaving(false);
    }
  }

  if (isLoading) {
    return (
      <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
        <p className={styles.loadingText}>Loading profile...</p>
      </PageShell>
    );
  }

  if (loadError || !profile) {
    return (
      <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
        <p className={styles.loadingText}>Couldn't load your profile. Please refresh and try again.</p>
      </PageShell>
    );
  }

  return (
    <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <UserIcon size={16} />
          Profile
        </div>
      </header>

      <main className={styles.main}>
        <div className={styles.profileHeader}>
          <div className={styles.avatar}>{profile.avatarInitial}</div>
          <div>
            <p className={styles.fullName}>{profile.fullName}</p>
            <p className={styles.subMeta}>{profile.title}</p>
          </div>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Full name</span>
          <span className={styles.infoValue}>{profile.fullName}</span>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Email</span>
          <span className={styles.infoValue}>{profile.email}</span>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Professor ID</span>
          <div className={styles.infoValueRow}>
            <span className={styles.infoValue}>{profile.professorId}</span>
            <button className={styles.editLink} onClick={openEditModal}>
              Edit
            </button>
          </div>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Date joined</span>
          <span className={styles.infoValue}>{profile.dateJoined}</span>
        </div>

        <h2 className={styles.sectionTitle}>Account</h2>

        <button
          className={styles.actionRow}
          onClick={() => setShowChangePassword(true)}
        >
          <span className={styles.actionIcon}>
            <KeyRound size={16} />
          </span>
          Change password
        </button>
      </main>

      {showEditModal && (
        <div className={styles.modalOverlay} onClick={() => setShowEditModal(false)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>Edit Professor ID</h3>
              <button className={styles.closeBtn} onClick={() => setShowEditModal(false)} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveEdit} className={styles.form}>
              <div className={styles.field}>
                <label className={styles.label}>Professor ID</label>
                <input
                  type="text"
                  className={styles.input}
                  value={professorId}
                  onChange={(e) => setProfessorId(e.target.value)}
                  placeholder="e.g. PROF-2026-014"
                />
              </div>

              {formError && <p className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSaving}>Save Changes</Button>
            </form>
          </div>
        </div>
      )}

      <ChangePasswordModal isOpen={showChangePassword} onClose={() => setShowChangePassword(false)} />
    </PageShell>
  );
}