import styles from './Navigation.module.css';

/** Presentation only: page actions and their permissions remain with the page. */
export default function Topbar({ title, children, className = '' }) {
  return <header data-page-topbar className={`${styles.topbar} ${className}`}>
    {title && <h1 className={styles.title}>{title}</h1>}
    {children}
  </header>;
}
