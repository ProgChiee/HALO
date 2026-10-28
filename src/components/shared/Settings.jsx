import { useState, useSyncExternalStore } from 'react';
import { Sun, Moon, Monitor } from 'lucide-react';
import PageShell from './PageShell';
import Topbar from './Topbar';
import Button from './Button';
import ChangePasswordModal from './ChangePasswordModal';
import { useAuth } from '../../context/login/useAuth';
import { useProfessorWorkflow } from '../../context/professor/useProfessorWorkflow';
import { useStudentWorkflow } from '../../context/student/useStudentWorkflow';
import { ADMIN_NAV_ITEMS, SUPERADMIN_NAV_ITEMS, PROFESSOR_NAV_ITEMS, STUDENT_NAV_ITEMS } from '../../data/navigationData';
import styles from './Settings.module.css';
const navigation = { admin: ADMIN_NAV_ITEMS, superadmin: SUPERADMIN_NAV_ITEMS, professor: PROFESSOR_NAV_ITEMS, student: STUDENT_NAV_ITEMS };
const labels = { admin: 'Admin', superadmin: 'Super Admin', professor: 'Professor', student: 'Student' };
const subscribe = callback => { window.addEventListener('halo:theme-change', callback); return () => window.removeEventListener('halo:theme-change', callback); };
const snapshot = () => window.haloTheme?.get() ?? 'light';
const options = [{value:'light', label:'Light', Icon:Sun}, {value:'dark', label:'Dark', Icon:Moon}, {value:'system', label:'System', Icon:Monitor}];
export default function Settings({ role }) {
  const { user } = useAuth();
  const professorWorkflow = useProfessorWorkflow();
  const studentWorkflow = useStudentWorkflow();
  const acting = professorWorkflow.acting || studentWorkflow.acting;
  const [showChangePassword, setShowChangePassword] = useState(user?.mustChangePassword === true);
  const theme = useSyncExternalStore(subscribe, snapshot, () => 'light');
  return <PageShell responsive navItems={navigation[role]} roleBadge={labels[role]}>
    <Topbar title="Settings" />
    <main className={styles.main}>
      {!acting && <section className={styles.card} aria-labelledby="security-title">
        <h2 id="security-title">Account &amp; Security</h2>
        {user?.mustChangePassword && <p role="alert">Change your temporary password before using {labels[role]} features.</p>}
        <div className={styles.securityRow}>
          <p>Change the password for your own account. You will need to sign in again afterward.</p>
          <Button onClick={event => { event.currentTarget.focus(); setShowChangePassword(true); }}>Change password</Button>
        </div>
      </section>}
      <section className={styles.card} aria-labelledby="appearance-title">
        <h2 id="appearance-title">Appearance</h2>
        <fieldset><legend>Theme</legend><div className={styles.options}>
          {options.map(({value,label,Icon}) => <label key={value} className={styles.option}>
            <input type="radio" name="halo-theme" value={value} checked={theme===value} onChange={() => window.haloTheme.set(value)} />
            <Icon size={18} aria-hidden="true" />{label}
          </label>)}
        </div></fieldset>
        <p>System follows your device appearance. Your preference is saved in this browser and also applies to acting modes.</p>
      </section>
    </main>
    {!acting && <ChangePasswordModal isOpen={showChangePassword} onClose={() => setShowChangePassword(false)} />}
  </PageShell>;
}
