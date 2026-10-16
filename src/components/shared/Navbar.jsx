import LogoutButton from './LogoutButton';
import { useAuth } from '../../context/login/useAuth';

import styles from './Navbar.module.css';

const ROLE_LABELS = {
  superadmin: 'Super Admin',
  admin: 'Admin',
  professor: 'Professor',
  student: 'AHRT Student',
};

export default function Navbar() {
  const { user, role } = useAuth();


  return (
    <nav className={styles.navbar}>
      <span className={styles.logo}>HALO</span>

      <div className={styles.right}>
        {role && <span className={styles.roleBadge} data-role={role}>{ROLE_LABELS[role]}</span>}
        {user && <span className={styles.userName}>{user.name}</span>}
        <LogoutButton className={styles.logoutBtn}>Log out</LogoutButton>
      </div>
    </nav>
  );
}