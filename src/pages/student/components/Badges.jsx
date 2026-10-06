import { logStudentError } from '../../../utils/studentDiagnostics';
import { useState, useEffect } from 'react';
import { Award } from 'lucide-react';
import StudentPageShell from './StudentPageShell';
import { getBadgesData } from '../../../services/student/studentService';
import styles from '../styles/Badges.module.css';

// Total number of possible badges, from the backend's BadgeType enum —
// used only to show "X earned of Y possible". Keep this in sync if the
// enum changes. The backend only returns EARNED badges (no "locked"
// catalog with names/descriptions for badges not yet earned), so a
// detailed "Locked" grid like the old mock version isn't possible unless
// your backend team adds a full badge catalog endpoint.
const TOTAL_POSSIBLE_BADGES = 12;

export default function Badges() {
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [earnedBadges, setEarnedBadges] = useState([]);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const data = await getBadgesData();
        if (isMounted) setEarnedBadges(data);
      } catch (err) {
        logStudentError('load-badges', err);
        if (isMounted) setLoadError(true);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    loadData();
    return () => { isMounted = false; };
  }, []);

  if (isLoading) {
    return (
      <StudentPageShell>
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading your badges...</p>
        </div>
      </StudentPageShell>
    );
  }

  if (loadError) {
    return (
      <StudentPageShell>
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load your badges. Please refresh and try again.</p>
        </div>
      </StudentPageShell>
    );
  }

  const remaining = Math.max(TOTAL_POSSIBLE_BADGES - earnedBadges.length, 0);

  return (
    <StudentPageShell>

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <Award size={16} />
            Badges
            <span className={styles.countPill}>{earnedBadges.length} earned</span>
          </div>
        </header>

        <main className={styles.main}>
          <h2 className={styles.sectionTitle}>Earned</h2>
          <div className={styles.badgesGrid}>
            {earnedBadges.map((badge) => (
              <div key={badge.id} className={styles.badgeCard}>
                <span className={styles.badgeEmoji}><Award size={28} /></span>
                <p className={styles.badgeTitle}>{badge.badgeName}</p>
                <p className={styles.badgeDescription}>{badge.description}</p>
                <span className={styles.earnedPill}>Earned</span>
              </div>
            ))}
            {earnedBadges.length === 0 && (
              <p className={styles.emptyState}>No badges earned yet — keep learning!</p>
            )}
          </div>

          {remaining > 0 && (
            <p className={styles.sectionTitle} style={{ marginTop: 24 }}>
              {remaining} more badge{remaining === 1 ? '' : 's'} to unlock
            </p>
          )}
        </main>
      </div>
    </StudentPageShell>
  );
}