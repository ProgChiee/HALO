import { Info } from 'lucide-react';
import { accountDisplay } from '../../../utils/adminAccountDisplay';
import styles from '../styles/Monitoring.module.css';

export default function IncompleteProfiles({ accounts, role }) {
  const rows = accounts.filter(account => account.profileIncomplete === true || (role === 'student' && account.profilePresent === false));
  if (!rows.length) return null;
  const value = text => typeof text === 'string' && text.trim() ? text : 'Not set';
  const year = text => text ? value(text).replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase()) : 'Not set';
  return (
    <section className={styles.incompleteCard} aria-label={`Incomplete ${role} profiles`}>
      <header className={styles.incompleteHeader}>
        <Info size={20} aria-hidden="true" />
        <div><h2>Profile Incomplete</h2><p>{rows.length} active {role}{rows.length === 1 ? '' : 's'} on this page need {role === 'student' ? 'academic profile details' : 'profile details'}.</p></div>
      </header>
      <ul className={styles.incompleteList}>
        {rows.map(account => <li key={account.userId} className={styles.incompleteRow}>
          <div className={styles.incompleteIdentity}><strong>{accountDisplay(account.name)}</strong><span>{value(account.email)}</span></div>
          <span className={styles.incompleteBadge}>Incomplete</span>
          <dl className={styles.incompleteDetails}>
            {role === 'student' && <><div><dt>Year</dt><dd>{year(account.yearLevel)}</dd></div><div><dt>Section</dt><dd>{value(account.section)}</dd></div></>}
            <div><dt>{role === 'student' ? 'Student ID' : 'Professor ID'}</dt><dd>{value(role === 'student' ? account.studentId : account.professorId)}</dd></div>
          </dl>
        </li>)}
      </ul>
    </section>
  );
}
