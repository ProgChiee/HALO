import { useSyncExternalStore } from 'react';
import { Settings as SettingsIcon, Sun, Moon, Monitor } from 'lucide-react';
import PageShell from './PageShell';
import { ADMIN_NAV_ITEMS, SUPERADMIN_NAV_ITEMS, PROFESSOR_NAV_ITEMS, STUDENT_NAV_ITEMS } from '../../data/navigationData';
import styles from './Settings.module.css';
const navigation = { admin: ADMIN_NAV_ITEMS, superadmin: SUPERADMIN_NAV_ITEMS, professor: PROFESSOR_NAV_ITEMS, student: STUDENT_NAV_ITEMS };
const labels = { admin: 'Admin', superadmin: 'Super Admin', professor: 'Professor', student: 'Student' };
const subscribe = callback => { window.addEventListener('halo:theme-change', callback); return () => window.removeEventListener('halo:theme-change', callback); };
const snapshot = () => window.haloTheme?.get() ?? 'light';
const options = [{value:'light', label:'Light', Icon:Sun}, {value:'dark', label:'Dark', Icon:Moon}, {value:'system', label:'System', Icon:Monitor}];
export default function Settings({ role }) {
  const theme = useSyncExternalStore(subscribe, snapshot, () => 'light');
  return <PageShell responsive navItems={navigation[role]} roleBadge={labels[role]}>
    <header className={styles.topbar}><SettingsIcon size={18} aria-hidden="true" />Settings</header>
    <main className={styles.main}>
      <h1>Settings</h1>
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
  </PageShell>;
}
