import { Link } from 'react-router-dom';
import styles from '../styles/Unauthorized.module.css';

export default function Unauthorized() {
  return (
    <div className={styles.wrapper}>
      <h1>403 — No access</h1>
      <p>Your account role doesn't have permission to view this page.</p>
      <Link to="/login">Back to login</Link>
    </div>
  );
}
