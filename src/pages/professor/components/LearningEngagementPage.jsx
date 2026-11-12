import { useState } from 'react';
import Topbar from '../../../components/shared/Topbar';
import PageShell from './ProfessorPageShell';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { professorEnrollmentOptions } from '../../../utils/professorEnrollment';
import LearningEngagement from './LearningEngagement';
import styles from '../styles/StudentProgress.module.css';

export default function LearningEngagementPage() {
  const [view, setView] = useState('CURRENT');
  const [enrollment, setEnrollment] = useState('ALL');
  return <PageShell responsive navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
    <Topbar title="Learning Engagement" />
    <main className={styles.main}><section className={styles.monitoringCard} aria-label="Learning engagement monitoring">
      <p className={styles.engagementHelp}>Review study activity and Mentor participation within your authorized subject scope.</p>
      <div className={styles.viewControls} role="group" aria-label="Account Status">
        <strong>Account Status</strong>
        <button className={styles.compactButton} aria-pressed={view === 'CURRENT'} onClick={() => setView('CURRENT')}>Current Students</button>
        <button className={styles.compactButton} aria-pressed={view === 'FORMER'} onClick={() => setView('FORMER')}>Former / Archived</button>
      </div>
      <p className={styles.engagementHelp}>Current means active accounts. Former / Archived means inactive or archived accounts. Enrollment membership is a separate filter.</p>
      <div className={styles.enrollmentFilter}><label htmlFor="engagement-enrollment-filter">Enrollment Status</label>
        <select id="engagement-enrollment-filter" value={enrollment} onChange={event => setEnrollment(event.target.value)}>
          {professorEnrollmentOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </div>
      <LearningEngagement key={`${view}-${enrollment}`} view={view} enrollment={enrollment} />
    </section></main>
  </PageShell>;
}
