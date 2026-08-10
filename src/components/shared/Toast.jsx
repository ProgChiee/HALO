import { CheckCircle, XCircle, Info, X } from 'lucide-react';
import styles from './Toast.module.css';

const ICONS = {
  success: CheckCircle,
  error: XCircle,
  info: Info,
};

export default function Toast({ message, type = 'info', onClose }) {
  const Icon = ICONS[type] || Info;

  return (
    <div className={`${styles.toast} ${styles[type]}`}>
      <Icon size={18} className={styles.icon} />
      <span className={styles.message}>{message}</span>
      <button className={styles.closeBtn} onClick={onClose} aria-label="Dismiss">
        <X size={14} />
      </button>
    </div>
  );
}