import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import styles from './Breadcrumb.module.css';

export default function Breadcrumb({ items, onNavigate }) {
  return <nav aria-label="Breadcrumb" className={styles.trail}>
    <ol className={styles.list}>{items.map(({ label, to, title }, index) =>
      <li key={to || 'current'}>
        {index > 0 && <ChevronRight size={14} aria-hidden="true" />}
        {index === items.length - 1
          ? <span aria-current="page" className={styles.current}>{label}{title && <span className={styles.title} title={title}> — {title}</span>}</span>
          : <Link to={to} onClick={onNavigate}>{label}</Link>}
      </li>)}</ol>
  </nav>;
}
