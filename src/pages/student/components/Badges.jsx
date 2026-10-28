import Topbar from '../../../components/shared/Topbar';
import { useStudentWorkflow } from '../../../context/student/useStudentWorkflow';
import { logStudentError } from '../../../utils/studentDiagnostics';
import { useCallback } from 'react';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { Award } from 'lucide-react';
import { getBadgeArtwork } from '../../../utils/badgeArtwork';
import StudentPageShell from './StudentPageShell';
import styles from '../styles/Badges.module.css';

function BadgeCard({ badge, earned }) {
  const artwork = getBadgeArtwork(badge.badgeType);
  return <li className={`${styles.badgeCard} ${earned ? styles.earnedCard : styles.lockedCard}`}>
    <span className={styles.badgeIcon}>
      {artwork ? <img className={styles.badgeArtwork} src={artwork} alt={`${badge.badgeName} badge`} loading="lazy" />
        : <Award size={64} strokeWidth={1.6} aria-hidden="true" />}
    </span>
    <h3 className={styles.badgeTitle}>{badge.badgeName}</h3>
    <p className={styles.badgeDescription}>{badge.description}</p>
    <span className={`${styles.statusPill} ${earned ? styles.earnedPill : styles.lockedPill}`}>{earned ? 'Earned' : 'Locked'}</span>
  </li>;
}

export default function Badges() {
  const { api } = useStudentWorkflow();
  const loadEarned = useCallback(async config => {
    try {
      const data = await api.getBadgesData(config);
      if (!Array.isArray(data)) throw new Error('Invalid badges response');
      return data;
    } catch (error) { logStudentError('load-badges', error); throw error; }
  }, [api]);
  const loadCatalog = useCallback(async config => {
    try {
      const data = await api.getBadgeCatalog(config);
      if (!Array.isArray(data) || data.some(badge => !badge || typeof badge.badgeType !== 'string' || typeof badge.badgeName !== 'string' || typeof badge.description !== 'string')
          || new Set(data.map(badge => badge.badgeType)).size !== data.length) throw new Error('Invalid badge catalog');
      return data;
    } catch (error) { logStudentError('load-badges', error); throw error; }
  }, [api]);
  const earned = useRemoteData(loadEarned, []);
  const catalog = useRemoteData(loadCatalog, []);

  if (earned.isLoading || earned.error) return <StudentPageShell>
      <Topbar title="Badges" />
    <div className={styles.contentArea}>
      {earned.isLoading ? <p role="status" className={styles.loadingText}>Loading your badges...</p> : <div role="alert" className={styles.feedback}>
        <p>Couldn't load your badges. Please try again.</p><button className={styles.retryButton} onClick={earned.reload}>Retry badges</button>
      </div>}
    </div>
  </StudentPageShell>;

  const earnedTypes = new Set(earned.data.map(badge => badge.badgeType));
  const locked = catalog.data.filter(badge => !earnedTypes.has(badge.badgeType));
  return <StudentPageShell>
    <div className={styles.contentArea}>
      <Topbar><div className={styles.breadcrumb}>
        <Award size={16} aria-hidden="true" />Badges
        <span className={styles.countPill}>{earned.data.length} earned</span>
      </div></Topbar>
      <main className={styles.main}>
        <section aria-labelledby="earned-badges-title">
          <div className={styles.sectionHeader}><h2 id="earned-badges-title" className={styles.sectionTitle}>Earned</h2><span className={styles.sectionCount}>{earned.data.length} {earned.data.length === 1 ? 'badge' : 'badges'}</span></div>
          {earned.data.length ? <ul className={styles.badgesGrid}>{earned.data.map(badge => <BadgeCard key={badge.id} badge={badge} earned />)}</ul>
            : <p className={styles.emptyState}>No badges earned yet — keep learning!</p>}
        </section>
        <section aria-labelledby="locked-badges-title">
          <div className={styles.sectionHeader}><h2 id="locked-badges-title" className={styles.sectionTitle}>Badges to Unlock</h2>
            {!catalog.isLoading && !catalog.error && <span className={styles.sectionCount}>{locked.length} remaining</span>}
          </div>
          <p className={styles.sectionDescription}>Your next milestones. Each badge shows what you need to achieve.</p>
          {catalog.isLoading ? <p role="status" className={styles.loadingText}>Loading badges to unlock...</p>
            : catalog.error ? <div role="alert" className={styles.feedback}><p>Couldn't load badges to unlock. Your earned badges are still shown above.</p><button className={styles.retryButton} onClick={catalog.reload}>Retry badge catalog</button></div>
            : locked.length ? <ul className={styles.badgesGrid}>{locked.map(badge => <BadgeCard key={badge.badgeType} badge={badge} earned={false} />)}</ul>
            : <p className={styles.emptyState}>{catalog.data.length ? 'You have earned every available badge. Well done!' : 'No additional badges are currently available.'}</p>}
        </section>
      </main>
    </div>
  </StudentPageShell>;
}
