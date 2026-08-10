import styles from './Button.module.css';

/**
 * Usage:
 * <Button onClick={handleSave}>Save changes</Button>
 * <Button variant="secondary" onClick={handleCancel}>Cancel</Button>
 * <Button type="submit" isLoading={isSubmitting}>Login</Button>
 */
export default function Button({
  children,
  onClick,
  variant = 'primary', // 'primary' | 'secondary' | 'danger'
  type = 'button',
  disabled = false,
  isLoading = false,
}) {
  const variantClass = styles[variant] ?? styles.primary;

  return (
    <button
      type={type}
      className={`${styles.btn} ${variantClass}`}
      onClick={onClick}
      disabled={disabled || isLoading}
    >
      {isLoading ? <span className={styles.spinner} aria-label="Loading" /> : children}
    </button>
  );
}