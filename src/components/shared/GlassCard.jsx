import styles from './GlassCard.module.css';

/**
 * The base glassmorphic container used across all dashboards.
 * Usage: <GlassCard><h3>Title</h3><p>Content</p></GlassCard>
 */
export default function GlassCard({ children, className = '' }) {
  return <div className={`${styles.card} ${className}`}>{children}</div>;
}
